package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tracking billing 261105-mm1: a run left RUNNING by an earlier process becomes FAILED at start, with a text that
 * asks to start it again; runs in other statuses and runs claimed after the start keep theirs.
 */
class InterruptedRunRecoveryTests extends BillingScenarioBase {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2024, 6, 1, 12, 0);
    private static final LocalDateTime BEFORE = STARTED_AT.minusDays(1);

    @Autowired
    private InterruptedRunRecovery recovery;
    @Autowired
    private BillingRunRepository billingRunRepository;

    @Test
    void aRunClaimedBeforeTheStartIsFailed() {
        UUID crashed = save("Abr_YM-2024-3", BillingRunStatus.RUNNING, BEFORE, null);

        recovery.failRunsClaimedBefore(STARTED_AT);

        BillingRun run = billingRunRepository.findById(crashed).orElseThrow();
        assertThat(run.getRunStatus(), is(BillingRunStatus.FAILED));
        assertThat(run.getErrorSummary(), is("Berechnung abgebrochen (billing wurde neu gestartet)."
                + " Bitte den Lauf erneut starten."));
    }

    @Test
    void otherStatusesAndRunsClaimedAfterTheStartAreUnchanged() {
        UUID preview = save("Abr_YM-2024-4", BillingRunStatus.NEW, BEFORE, null);
        UUID done = save("Abr_YM-2024-5", BillingRunStatus.DONE, BEFORE, null);
        UUID failed = save("Abr_YM-2024-6", BillingRunStatus.FAILED, BEFORE, "eigener Fehler");
        UUID running = save("Abr_YM-2024-7", BillingRunStatus.RUNNING, STARTED_AT.plusSeconds(1), null);

        recovery.failRunsClaimedBefore(STARTED_AT);

        assertThat(billingRunRepository.findById(preview).orElseThrow().getRunStatus(), is(BillingRunStatus.NEW));
        assertThat(billingRunRepository.findById(done).orElseThrow().getRunStatus(), is(BillingRunStatus.DONE));
        BillingRun failedRun = billingRunRepository.findById(failed).orElseThrow();
        assertThat(failedRun.getRunStatus(), is(BillingRunStatus.FAILED));
        assertThat(failedRun.getErrorSummary(), is("eigener Fehler"));
        BillingRun runningRun = billingRunRepository.findById(running).orElseThrow();
        assertThat(runningRun.getRunStatus(), is(BillingRunStatus.RUNNING));
        assertThat(runningRun.getErrorSummary(), nullValue());
    }

    private UUID save(String identifier, BillingRunStatus status, LocalDateTime at, String errorSummary) {
        BillingRun run = new BillingRun();
        run.setTenantId(tenant);
        run.setClearingPeriodType(PERIOD_TYPE);
        run.setClearingPeriodIdentifier(identifier);
        run.setRunStatus(status);
        run.setRunStatusDateTime(at);
        run.setErrorSummary(errorSummary);
        return billingRunRepository.save(run).getId();
    }
}
