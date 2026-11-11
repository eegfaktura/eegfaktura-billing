package org.vfeeg.eegfaktura.billing.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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

    private final Logger logger = (Logger) LoggerFactory.getLogger(InterruptedRunRecovery.class);
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    @BeforeEach
    void attach() {
        lines.start();
        logger.addAppender(lines);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(lines);
    }

    @Test
    void aRunClaimedBeforeTheStartIsFailed() {
        UUID crashed = save("Abr_YM-2024-3", BillingRunStatus.RUNNING, BEFORE, null);

        recovery.failRunsClaimedBefore(STARTED_AT);

        BillingRun run = billingRunRepository.findById(crashed).orElseThrow();
        assertThat(run.getRunStatus(), is(BillingRunStatus.FAILED));
        assertThat(run.getErrorSummary(), is("Berechnung abgebrochen (billing wurde neu gestartet)."
                + " Bitte den Lauf erneut starten."));
    }

    /** eegfaktura-dev#54 #11: one warning per run, with its id, its community and event.action. */
    @Test
    void eachFailedRunIsLoggedWithItsIdAndTenant() {
        UUID crashed = save("Abr_YM-2024-3", BillingRunStatus.RUNNING, BEFORE, null);

        recovery.failRunsClaimedBefore(STARTED_AT);

        assertThat(own(), hasSize(1));
        ILoggingEvent line = own().get(0);
        assertThat(line.getLevel(), is(Level.WARN));
        assertThat(line.getFormattedMessage(), containsString(crashed.toString()));
        assertThat(fields(line).get("event.action"), is("billing_run.recovered"));
        assertThat(fields(line).get("tenant.id"), is(tenant.toUpperCase()));
    }

    /** The column is nullable: a RUNNING run without a claim time blocked its period for good (#54 #11). */
    @Test
    void aRunningRunWithoutAClaimTimeIsFailed() {
        UUID noTime = save("Abr_YM-2024-8", BillingRunStatus.RUNNING, null, null);

        recovery.failRunsClaimedBefore(STARTED_AT);

        assertThat(billingRunRepository.findById(noTime).orElseThrow().getRunStatus(), is(BillingRunStatus.FAILED));
        assertThat(own().get(0).getFormattedMessage(), containsString(noTime.toString()));
    }

    /** A claim time after now cannot come from this process (clock or zone changed): kept, with a warning. */
    @Test
    void aRunClaimedInTheFutureIsKeptWithAWarning() {
        UUID future = save("Abr_YM-2024-9", BillingRunStatus.RUNNING, LocalDateTime.now().plusDays(1), null);

        try {
            recovery.failRunsClaimedBefore(STARTED_AT);
            assertThat(billingRunRepository.findById(future).orElseThrow().getRunStatus(),
                    is(BillingRunStatus.RUNNING));
        } finally {
            // a RUNNING row in the future would stay RUNNING for every later recovery of the shared database
            billingRunRepository.deleteById(future);
        }
        assertThat(own(), hasSize(1));
        assertThat(own().get(0).getLevel(), is(Level.WARN));
        assertThat(own().get(0).getFormattedMessage(), containsString(future.toString()));
        assertThat(fields(own().get(0)).get("event.action"), is("billing_run.claimed_in_future"));
    }

    /** A run changed between the read and the update: one summary line without ids, none naming a run. */
    @Test
    void aRunChangedMeanwhileGivesOneSummaryLine() {
        BillingRunRepository repository = Mockito.mock(BillingRunRepository.class);
        BillingRun run = new BillingRun();
        run.setId(UUID.randomUUID());
        run.setTenantId(tenant);
        run.setRunStatus(BillingRunStatus.RUNNING);
        run.setRunStatusDateTime(BEFORE);
        Mockito.when(repository.findByRunStatus(BillingRunStatus.RUNNING)).thenReturn(List.of(run));
        Mockito.when(repository.failRunning(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(0);

        new InterruptedRunRecovery(repository).failRunsClaimedBefore(STARTED_AT);

        assertThat(lines.list, hasSize(1));
        assertThat(fields(lines.list.get(0)).get("event.action"), is("billing_run.recovered"));
        assertThat(lines.list.get(0).getFormattedMessage().contains(run.getId().toString()), is(false));
    }

    /** The lines of this test's community: the query sees every community of the shared database. */
    private List<ILoggingEvent> own() {
        return lines.list.stream().filter(line -> tenant.toUpperCase().equals(fields(line).get("tenant.id"))).toList();
    }

    private static Map<String, Object> fields(ILoggingEvent line) {
        return line.getKeyValuePairs().stream().filter(kv -> kv.value != null)
                .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
    }

    @Test
    void otherStatusesAndRunsClaimedAfterTheStartAreUnchanged() {
        UUID preview = save("Abr_YM-2024-4", BillingRunStatus.NEW, BEFORE, null);
        UUID done = save("Abr_YM-2024-5", BillingRunStatus.DONE, BEFORE, null);
        UUID failed = save("Abr_YM-2024-6", BillingRunStatus.FAILED, BEFORE, "eigener Fehler");
        UUID running = save("Abr_YM-2024-7", BillingRunStatus.RUNNING, STARTED_AT.plusSeconds(1), null);

        recovery.failRunsClaimedBefore(STARTED_AT);

        assertThat(own(), is(List.of()));

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
