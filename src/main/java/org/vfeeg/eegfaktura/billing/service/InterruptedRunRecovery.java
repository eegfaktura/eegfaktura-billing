package org.vfeeg.eegfaktura.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.logging.LogFields;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * A run left RUNNING by a crash or restart was never recovered and blocked its period (tracking billing 261105-mm1).
 * At start, every run claimed before this JVM started, or without a claim time, is set to FAILED; FAILED can be
 * claimed again, so the run can be restarted.
 *
 * <p>Assumes one billing instance at a time: the Deployment has one replica with strategy {@code Recreate}
 * (eegfaktura-gitops {@code base/billing.yaml}), so no other process can be calculating a run claimed before this
 * one started. With several instances or a rolling update this would fail a run another instance is still working on.
 * Runs claimed after the JVM start (requests accepted before the application is ready) keep their status.
 */
@Component
@Slf4j
public class InterruptedRunRecovery {

    static final String ERROR_SUMMARY = "Berechnung abgebrochen (billing wurde neu gestartet). Bitte den Lauf erneut"
            + " starten.";

    private final BillingRunRepository billingRunRepository;

    public InterruptedRunRecovery(final BillingRunRepository billingRunRepository) {
        this.billingRunRepository = billingRunRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failRunsOfEarlierProcesses() {
        // run_status_date_time is written with LocalDateTime.now(), so the JVM start in the same default zone
        failRunsClaimedBefore(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()), ZoneId.systemDefault()));
    }

    /**
     * Fails the runs claimed before {@code startedAt} and those without a claim time (the column is nullable,
     * eegfaktura-dev#54 #11), one warning per run with its id and community. A claim time later than now cannot come
     * from this process (clock or time zone changed): the run stays as it is, with a warning naming it.
     */
    int failRunsClaimedBefore(final LocalDateTime startedAt) {
        final LocalDateTime now = LocalDateTime.now();
        final List<BillingRun> interrupted = new ArrayList<>();
        for (BillingRun run : billingRunRepository.findByRunStatus(BillingRunStatus.RUNNING)) {
            final LocalDateTime claimedAt = run.getRunStatusDateTime();
            if (claimedAt == null || claimedAt.isBefore(startedAt)) {
                interrupted.add(run);
            } else if (claimedAt.isAfter(now)) {
                log.atWarn().addKeyValue(LogFields.EVENT_ACTION, "billing_run.claimed_in_future")
                        .addKeyValue(LogFields.TENANT_ID, LogFields.tenant(run.getTenantId()))
                        .log("Billing run {} is RUNNING with a claim time in the future, left as it is", run.getId());
            }
        }
        if (interrupted.isEmpty()) {
            return 0;
        }
        final int failed = billingRunRepository.failRunning(interrupted.stream().map(BillingRun::getId).toList(),
                startedAt, ERROR_SUMMARY, now);
        if (failed != interrupted.size()) {
            // a run changed between the read and the update: which ones were set is not known, so no ids
            log.atWarn().addKeyValue(LogFields.EVENT_ACTION, "billing_run.recovered")
                    .log("{} of {} billing runs left RUNNING by an earlier process set to FAILED", failed,
                            interrupted.size());
            return failed;
        }
        for (BillingRun run : interrupted) {
            log.atWarn().addKeyValue(LogFields.EVENT_ACTION, "billing_run.recovered")
                    .addKeyValue(LogFields.TENANT_ID, LogFields.tenant(run.getTenantId()))
                    .log("Billing run {} left RUNNING by an earlier process set to FAILED", run.getId());
        }
        return failed;
    }
}
