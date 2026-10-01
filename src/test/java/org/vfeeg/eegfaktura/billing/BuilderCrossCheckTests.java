package org.vfeeg.eegfaktura.billing;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.PostgresContainerHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Cross-check of the test builders: the master data and allocations built in code must give the
 * same five documents and gross amounts as the SQL fixture in {@code BillingIntegrationTests}.
 */
@SpringBootTest
@Transactional
class BuilderCrossCheckTests {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BillingConfigService billingConfigService;
    @Autowired
    FileDataRepository fileDataRepository;
    @Autowired
    BillingService billingService;
    @Autowired
    BillingDocumentService billingDocumentService;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresContainerHolder.register(registry);
    }

    @Test
    void builtLegacyWorldGivesTheDocumentsOfTheSqlFixture() {
        BillingRunFixture fixture = BillingRunFixture.legacyWorld().insertInto(jdbc);
        assertThat(fixture.masterCount(), is(5));
        fixture.createConfig(billingConfigService, fileDataRepository, false);

        DoBillingResults results = billingService.doBilling(fixture.params("QUARTERLY", "Abr_YQ-2023-3", true));
        assertThat(results.getBillingRunId(), notNullValue());
        assertThat(results.getParticipantAmounts().size(), is(3));

        List<BillingDocumentDTO> documents = billingDocumentService.findByBillingRunId(results.getBillingRunId());
        assertThat(documents, hasSize(5));
        assertThat(gross(documents, "Sonne GmbH", BillingDocumentType.INVOICE), comparesEqualTo(new BigDecimal("35.88")));
        assertThat(gross(documents, "Sonne GmbH", BillingDocumentType.INFO), comparesEqualTo(new BigDecimal("762.55")));
        assertThat(gross(documents, "Mag. Felix Glück Msc", BillingDocumentType.INVOICE),
                comparesEqualTo(new BigDecimal("125.21")));
        assertThat(gross(documents, "Fridolin Fröhlich MBA", BillingDocumentType.INVOICE),
                comparesEqualTo(new BigDecimal("10.00")));
        assertThat(gross(documents, "Fridolin Fröhlich MBA", BillingDocumentType.CREDIT_NOTE),
                comparesEqualTo(new BigDecimal("431.68")));
    }

    private static BigDecimal gross(List<BillingDocumentDTO> documents, String recipient, BillingDocumentType type) {
        return documents.stream()
                .filter(d -> d.getRecipientName().equals(recipient) && d.getBillingDocumentType() == type)
                .findFirst().orElseThrow(() -> new AssertionError("no " + type + " for " + recipient))
                .getGrossAmountInEuro();
    }
}
