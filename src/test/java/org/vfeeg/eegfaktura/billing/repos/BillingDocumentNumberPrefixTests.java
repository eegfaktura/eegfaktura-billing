package org.vfeeg.eegfaktura.billing.repos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * known-errors #15 against PostgreSQL: numbers stored with the untrimmed prefix " R" (before the fix) and new ones
 * with "R" are one sequence, because both print as "R…"; the configuration stores its prefixes trimmed.
 */
class BillingDocumentNumberPrefixTests extends BillingScenarioBase {

    @Autowired
    private BillingDocumentNumberGenerator generator;

    @Test
    void numbersOfTheUntrimmedPrefixContinueInOneSequence() {
        jdbc.update("insert into billing_document_number (id, date_created, last_updated, document_number,"
                + " document_type, prefix, sequence_number, tenant_id, year)"
                + " values (?, now(), now(), 'R202400041', 0, ' R', 41, ?, 2024)", UUID.randomUUID(), tenant);

        BillingDocumentNumber trimmed = generator.getNext(tenant, 2024, "R", 1L, 5);
        BillingDocumentNumber untrimmed = generator.getNext(tenant, 2024, " R ", 1L, 5);

        assertThat(trimmed.getDocumentNumber(), is("R202400042"));
        assertThat(untrimmed.getDocumentNumber(), is("R202400043"));
        assertThat(untrimmed.getPrefix(), is("R"));
    }

    @Test
    void theConfigurationStoresItsPrefixesTrimmed() {
        BillingConfigDTO dto = new BillingConfigDTO();
        dto.setTenantId(tenant);
        dto.setInvoiceNumberPrefix(" R ");
        dto.setCreditNoteNumberPrefix("G ");

        billingConfigService.create(dto);

        assertThat(jdbc.queryForObject("select invoice_number_prefix || '|' || credit_note_number_prefix"
                + " from billing_config where tenant_id = ?", String.class, tenant), is("R|G"));
    }
}
