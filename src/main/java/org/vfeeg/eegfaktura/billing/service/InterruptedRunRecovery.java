package org.vfeeg.eegfaktura.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A run left RUNNING by a crash or restart was never recovered and blocked its period (tracking billing 261105-mm1).
 * At start, every run claimed before this JVM started is set to FAILED; FAILED can be claimed again, so the run can
 * be restarted.
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

    int failRunsClaimedBefore(final LocalDateTime startedAt) {
        int failed = billingRunRepository.failRunsClaimedBefore(startedAt, ERROR_SUMMARY, LocalDateTime.now());
        if (failed > 0) {
            log.warn("{} billing runs left RUNNING by an earlier process set to FAILED", failed);
        }
        return failed;
    }
}
