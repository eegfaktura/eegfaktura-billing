package org.vfeeg.eegfaktura.billing;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Statusmaschine des asynchronen Abrechnungslaufs (konzept-async-billing-run.md):
 * atomarer Claim, FAILED-Neustart, Preview -> NEW, DONE/CANCELLED-Guard.
 */
@SpringBootTest
@Testcontainers
@Transactional
class AsyncBillingRunTests {

    @Container
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withUsername("sa")
            .withPassword("sa")
            .withReuse(true);

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    @Autowired
    BillingService billingService;

    @Autowired
    BillingRunRepository billingRunRepository;

    private DoBillingParams params(String tenant, boolean preview) {
        DoBillingParams params = new DoBillingParams();
        params.setTenantId(tenant);
        params.setClearingPeriodType("QUARTERLY");
        params.setClearingPeriodIdentifier("Abr_YQ-2023-3");
        params.setPreview(preview);
        params.setAllocations(new Allocation[0]);
        return params;
    }

    @Test
    void claimIsAtomic_onlyOneWinner() {
        BillingRun run = billingService.acceptBillingRun(params("TE900001", false));
        assertThat(run.getRunStatus(), equalTo(BillingRunStatus.NEW));

        assertThat(billingService.claimBillingRun(run.getId()), is(true));
        assertThat(billingService.claimBillingRun(run.getId()), is(false)); // zweiter Start verliert

        BillingRun reloaded = billingRunRepository.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getRunStatus(), equalTo(BillingRunStatus.RUNNING));
    }

    @Test
    void revertClaimRestoresPreviousStatus() {
        BillingRun run = billingService.acceptBillingRun(params("TE900002", false));
        billingService.claimBillingRun(run.getId());

        billingService.revertBillingRunClaim(run.getId(), BillingRunStatus.NEW);

        BillingRun reloaded = billingRunRepository.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getRunStatus(), equalTo(BillingRunStatus.NEW));
        assertThat(billingService.claimBillingRun(run.getId()), is(true)); // wieder startbar
    }

    @Test
    void failedRunPersistsErrorSummaryAndIsRestartable() {
        BillingRun run = billingService.acceptBillingRun(params("TE900003", false));
        billingService.claimBillingRun(run.getId());

        billingService.markBillingRunFailed(run.getId(), "Abrechnung fehlgeschlagen: Testfehler");

        BillingRun reloaded = billingRunRepository.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getRunStatus(), equalTo(BillingRunStatus.FAILED));
        assertThat(reloaded.getErrorSummary(), containsString("Testfehler"));

        // FAILED -> erneuter Start moeglich; Claim raeumt errorSummary ab
        assertThat(billingService.claimBillingRun(run.getId()), is(true));
        reloaded = billingRunRepository.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getRunStatus(), equalTo(BillingRunStatus.RUNNING));
        assertThat(reloaded.getErrorSummary(), nullValue());
    }

    @Test
    @Sql("/billing_master_data.sql") // legt base.billing_masterdata an (View-Ersatz im Testcontainer)
    void previewRunEndsAsNew_finalRunEndsAsDone() {
        // Ohne Masterdata-Fixture rechnet der Lauf 0 Teilnehmer - fuer die
        // Statusmaschine genau richtig (kein Dokument, aber Statuswechsel).
        BillingRun previewRun = billingService.acceptBillingRun(params("TE900004", true));
        billingService.claimBillingRun(previewRun.getId());
        billingService.executeBillingRun(previewRun.getId(), params("TE900004", true));
        assertThat(billingRunRepository.findById(previewRun.getId()).orElseThrow().getRunStatus(),
                equalTo(BillingRunStatus.NEW));

        BillingRun finalRun = billingService.acceptBillingRun(params("TE900005", false));
        billingService.claimBillingRun(finalRun.getId());
        billingService.executeBillingRun(finalRun.getId(), params("TE900005", false));
        assertThat(billingRunRepository.findById(finalRun.getId()).orElseThrow().getRunStatus(),
                equalTo(BillingRunStatus.DONE));
    }

    @Test
    void doneAndCancelledRunsRejectNewStarts() {
        BillingRun run = billingService.acceptBillingRun(params("TE900006", false));
        run.setRunStatus(BillingRunStatus.DONE);
        billingRunRepository.save(run);
        assertThrows(BillingService.BillingRunAlreadyClosedException.class,
                () -> billingService.acceptBillingRun(params("TE900006", false)));

        run.setRunStatus(BillingRunStatus.CANCELLED);
        billingRunRepository.save(run);
        assertThrows(BillingService.BillingRunAlreadyClosedException.class,
                () -> billingService.acceptBillingRun(params("TE900006", false)));

        // und ein abgeschlossener Lauf ist nicht claimbar
        assertThat(billingService.claimBillingRun(run.getId()), is(false));
    }

    @Test
    void prepostedDocumentDateIsRejected() {
        DoBillingParams params = params("TE900007", false);
        params.setClearingDocumentDate(java.time.LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> billingService.acceptBillingRun(params));
    }
}
