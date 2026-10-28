package org.vfeeg.eegfaktura.billing.concurrency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.rest.BillingResource;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.ConcurrencyAndMailBase;
import org.vfeeg.eegfaktura.billing.support.Gate;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Parallel billing runs through the resource bean. Since the asynchronous run (upstream #68, 2026-10-21)
 * the resource holds no per-tenant lock any more: {@code startBilling} accepts a run (202 + billingRunId),
 * claims it atomically (RUNNING per community and period, exactly one winner) and hands the calculation to
 * the {@link org.vfeeg.eegfaktura.billing.service.BillingRunLauncher}; a second start of the same period
 * answers 409. Runs of one community for <i>different</i> periods now run side by side, so the number
 * assignment in the service (known-errors #15) is reachable through the resource. Deterministic: the spies
 * stop the runs at {@link Gate}s; the only wait is the bounded poll for a run's final status.
 */
class ConcurrentBillingRunTests extends ConcurrencyAndMailBase {

    private static final Duration WHOLE_TEST = Duration.ofSeconds(60);
    private static final EnumSet<BillingRunStatus> FINISHED =
            EnumSet.of(BillingRunStatus.DONE, BillingRunStatus.FAILED, BillingRunStatus.CANCELLED);

    @Autowired
    private BillingResource billingResource;

    private final AtomicInteger inside = new AtomicInteger();
    private final AtomicInteger maxInside = new AtomicInteger();

    /**
     * The claim (formerly F3, #14, the lock): run A of a period is held inside the calculation. A second
     * start of the same period is refused with 409 and A's run id, nothing enters. After A has finished
     * (DONE) a third start is refused as well. Only one run of a period is ever inside the calculation.
     */
    @Test
    void secondStartOfARunningPeriodIsRefused() {
        Map<String, Gate> gates = Map.of(PERIOD, new Gate("run A"));
        holdRunsAt(gates);
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            ResponseEntity<DoBillingResults> a = result(start(tenant, world.params(PERIOD_TYPE, PERIOD, false)), "run A");
            assertThat(a.getStatusCode(), is(HttpStatus.ACCEPTED));
            UUID runId = a.getBody().getBillingRunId();
            assertThat(runId, is(notNullValue()));
            gates.get(PERIOD).awaitEntered();

            ResponseEntity<DoBillingResults> b = result(start(tenant, world.params(PERIOD_TYPE, PERIOD, false)),
                    "second start of the running period");
            assertThat(b.getStatusCode(), is(HttpStatus.CONFLICT));
            assertThat(b.getBody().getBillingRunId(), is(runId));

            gates.get(PERIOD).release();
            assertThat(awaitFinished(runId), is(BillingRunStatus.DONE));
            assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042"));

            ResponseEntity<DoBillingResults> c = result(start(tenant, world.params(PERIOD_TYPE, PERIOD, false)),
                    "start after the period is done");
            assertThat(c.getStatusCode(), is(HttpStatus.CONFLICT));
            assertThat("runs of one period inside the calculation at the same time", maxInside.get(), is(1));
        });
    }

    /**
     * Two starts of one period at the same moment (the first-creation race the resource guards with the
     * unique index and the claim): exactly one is accepted, the other gets 409, and the one run completes.
     */
    @Test
    void simultaneousStartsOfOnePeriodAcceptExactlyOne() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        CyclicBarrier both = new CyclicBarrier(2);

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            List<Future<ResponseEntity<DoBillingResults>>> starts = List.of(1, 2).stream()
                    .map(i -> asTenant(tenant, () -> {
                        DoBillingParams params = withDate(world.params(PERIOD_TYPE, PERIOD, false));
                        assertThat("both starts ready", Gate.meet(both, Gate.TIMEOUT), is(true));
                        return billingResource.startBilling(params);
                    }))
                    .toList();
            List<HttpStatus> codes = starts.stream().map(s -> (HttpStatus) result(s, "start").getStatusCode()).toList();
            assertThat(codes, containsInAnyOrder(HttpStatus.ACCEPTED, HttpStatus.CONFLICT));

            UUID runId = starts.stream().map(s -> result(s, "start").getBody().getBillingRunId())
                    .filter(id -> id != null).findFirst().orElseThrow();
            assertThat(awaitFinished(runId), is(BillingRunStatus.DONE));
            assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042"));
        });
    }

    /** A run of another community is not blocked by a held run (the claim is per community and period). */
    @Test
    void runOfAnotherCommunityFinishesWhileTheFirstIsHeld() {
        String held = tenant + "H";
        Gate gate = new Gate("run of " + held);
        doAnswer(invocation -> {
            DoBillingParams params = invocation.getArgument(1);
            if (held.equals(params.getTenantId())) {
                gate.pass();
            }
            return invocation.callRealMethod();
        }).when(billingServiceSpy).executeBillingRun(any(), any());
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            ResponseEntity<DoBillingResults> first = result(start(held, params(held, PERIOD)), "held run");
            assertThat(first.getStatusCode(), is(HttpStatus.ACCEPTED));
            gate.awaitEntered();

            ResponseEntity<DoBillingResults> second = result(start(tenant, world.params(PERIOD_TYPE, PERIOD, false)),
                    "run of the other community while the first is held");
            assertThat(second.getStatusCode(), is(HttpStatus.ACCEPTED));
            assertThat(awaitFinished(second.getBody().getBillingRunId()), is(BillingRunStatus.DONE));
            assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042"));

            gate.release();
            awaitFinished(first.getBody().getBillingRunId());
        });
    }

    /**
     * F4 (#15) through the resource: two real runs (two periods) of one community started at the same moment;
     * both are accepted (different periods, different claims). Since the asynchronous run nothing serialised the two
     * calculations: both read the maximum, both took 42, the second run ended FAILED on the unique constraint
     * (tenant, document number). Since the joining fee final runs of one community run one at a time
     * ({@code FinalRunLock}, decided 28.10.2026: refused, not waiting): the second ends FAILED with a clear message
     * when the first is still running, or runs after it; never two documents with one number.
     */
    @Test
    void twoRunsOfOneCommunityAtOnceNeverShareANumber() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        CyclicBarrier start = new CyclicBarrier(2);

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            List<Future<ResponseEntity<DoBillingResults>>> runs = List.of("Abr_YQ-2024-2", "Abr_YQ-2024-3").stream()
                    .map(period -> asTenant(tenant, () -> {
                        DoBillingParams params = withDate(world.params(PERIOD_TYPE, period, false));
                        assertThat("both runs started", Gate.meet(start, Gate.TIMEOUT), is(true));
                        return billingResource.startBilling(params);
                    }))
                    .toList();

            int done = 0;
            for (Future<ResponseEntity<DoBillingResults>> run : runs) {
                ResponseEntity<DoBillingResults> accepted = result(run, "run");
                assertThat(accepted.getStatusCode(), is(HttpStatus.ACCEPTED));
                UUID runId = accepted.getBody().getBillingRunId();
                if (awaitFinished(runId) == BillingRunStatus.DONE) {
                    done++;
                } else {
                    assertThat(run(runId).getErrorSummary(), is(BUSY));
                }
            }
            assertThat("at least one run billed", done >= 1, is(true));
            List<String> numbers = documentNumbers();
            assertThat(numbers.size(), is(done));
            assertThat("distinct numbers", Set.copyOf(numbers).size(), is(done));
        });
    }

    /**
     * F4 (#15): the number is read (max + 1) and saved without a lock. Two runs of one community in the
     * service (where a second instance of the service would call it as well) would both read the maximum before
     * either saves, both take 42, and the second would fail on the unique constraint (tenant, document number).
     * Since {@code FinalRunLock} (refused, not waiting, decided 28.10.2026) the first holds the community's lock
     * while it reads the number (held at the barrier), so the second final run is refused at once.
     */
    @Test
    void secondFinalRunInTheServiceIsRefusedWhileTheFirstReadsItsNumber() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        CyclicBarrier bothRead = new CyclicBarrier(2);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            Object max = proceed(numberRepositorySpy, invocation);
            if (reads.incrementAndGet() <= 2) {
                Gate.meet(bothRead, Gate.NOT_EXPECTED);
            }
            return max;
        }).when(numberRepositorySpy).getMaxSequenceNumber(anyString(), anyInt(), anyString());

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            List<Future<DoBillingResults>> runs = List.of("Abr_YQ-2024-2", "Abr_YQ-2024-3").stream()
                    .map(period -> pool.submit(() -> billingService.doBilling(
                            withDate(world.params(PERIOD_TYPE, period, false)))))
                    .toList();

            int ok = 0;
            int refused = 0;
            for (Future<DoBillingResults> run : runs) {
                try {
                    assertThat(run.get(Gate.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).getAbstractText(),
                            is(RESULT_FINAL_OK));
                    ok++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause().getMessage(), is(BUSY_TEXT));
                    refused++;
                }
            }
            assertThat(ok, is(1));
            assertThat(refused, is(1));
            assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042"));
        });
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Stubs the calculation: count who is inside, stop at the gate named by the period, then calculate. */
    private void holdRunsAt(Map<String, Gate> gates) {
        doAnswer(invocation -> {
            DoBillingParams params = invocation.getArgument(1);
            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
            try {
                Gate gate = gates.get(params.getClearingPeriodIdentifier());
                if (gate != null) {
                    gate.pass();
                }
                return invocation.callRealMethod();
            } finally {
                inside.decrementAndGet();
            }
        }).when(billingServiceSpy).executeBillingRun(any(), any());
    }

    /** Starts a run through the resource as {@code tenantId}; the answer comes back at once (202, 409, …). */
    private Future<ResponseEntity<DoBillingResults>> start(String tenantId, DoBillingParams params) {
        return asTenant(tenantId, () -> billingResource.startBilling(withDate(params)));
    }

    /** The run's final status, polled within {@link Gate#TIMEOUT} (the launcher runs it in its own thread). */
    private BillingRunStatus awaitFinished(UUID runId) throws InterruptedException {
        long deadline = System.nanoTime() + Gate.TIMEOUT.toNanos();
        BillingRunDTO run;
        do {
            run = run(runId);
            if (FINISHED.contains(run.getRunStatus())) {
                return run.getRunStatus();
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("run " + runId + " still " + run.getRunStatus() + " after " + Gate.TIMEOUT
                + (run.getErrorSummary() != null ? ": " + run.getErrorSummary() : ""));
    }

    private static final String BUSY_TEXT =
            "Ein endgültiger Abrechnungslauf dieser Gemeinschaft läuft gerade – bitte nach dessen Ende erneut starten";
    private static final String BUSY = "Abrechnung fehlgeschlagen: " + BUSY_TEXT;

    private static DoBillingParams params(String tenantId, String period) {
        DoBillingParams params = BillingRunFixture.forTenant(tenantId).params(PERIOD_TYPE, period, false);
        return withDate(params);
    }

    private static DoBillingParams withDate(DoBillingParams params) {
        params.setClearingDocumentDate(REFERENCE_DATE);
        return params;
    }
}
