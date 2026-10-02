package org.vfeeg.eegfaktura.billing.concurrency;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.rest.BillingResource;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.ConcurrencyAndMailBase;
import org.vfeeg.eegfaktura.billing.support.Gate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Parallel billing runs through the resource bean (the per-tenant lock lives in {@link BillingResource},
 * known-errors #14) and in the service (number assignment, #15). Deterministic: the spies stop the runs at
 * {@link Gate}s; nothing sleeps, every wait is bounded.
 */
class ConcurrentBillingRunTests extends ConcurrencyAndMailBase {

    private static final Duration WHOLE_TEST = Duration.ofSeconds(60);

    @Autowired
    private BillingResource billingResource;

    private final AtomicInteger inside = new AtomicInteger();
    private final AtomicInteger maxInside = new AtomicInteger();

    /**
     * F3 (#14): run A holds the lock, B has fetched the same lock object and waits. A finishes and
     * {@code releaseLock} removes the entry; B enters and is held. Run D then gets a <i>new</i> lock object
     * and enters {@code doBilling} while B is inside. Correct: at most one run of a community is inside
     * {@code doBilling} at any time (D waits for B). The runs are stubbed; only the lock is under test.
     */
    @Test
    @Disabled("known-errors #14")
    void runsOfOneCommunityNeverOverlap() {
        Map<String, Gate> gates = Map.of("A", new Gate("run A"), "B", new Gate("run B"), "D", new Gate("run D"));
        holdRunsAt(gates);
        CountDownLatch bHasLockObject = new CountDownLatch(1);
        ThreadLocal<String> runName = new ThreadLocal<>();
        doAnswer(invocation -> {
            Object lock = invocation.callRealMethod();
            if ("B".equals(runName.get())) {
                bHasLockObject.countDown();
            }
            return lock;
        }).when(lockSpy).getLock(anyString());

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            Future<?> a = startRun(tenant, "A", runName);
            gates.get("A").awaitEntered();
            Future<?> b = startRun(tenant, "B", runName);
            Gate.await(bHasLockObject, "run B fetched the lock object of run A");
            gates.get("A").release();
            result(a, "run A");
            gates.get("B").awaitEntered();

            Future<?> d = startRun(tenant, "D", runName);
            boolean dEnteredWhileBInside = gates.get("D").enteredWithin(Gate.NOT_EXPECTED);
            gates.get("B").release();
            gates.get("D").release();
            result(b, "run B");
            result(d, "run D");

            assertThat("run D entered doBilling while run B was inside", dEnteredWhileBInside, is(false));
            assertThat("runs of one community inside doBilling at the same time", maxInside.get(), is(1));
        });
    }

    /** A run of another community is not blocked by a held run (the lock is per community). */
    @Test
    void runOfAnotherCommunityFinishesWhileTheFirstIsHeld() {
        String held = tenant + "H";
        Gate gate = new Gate("run of " + held);
        doAnswer(invocation -> {
            DoBillingParams params = invocation.getArgument(0);
            if (!held.equals(params.getTenantId())) {
                return invocation.callRealMethod();
            }
            gate.pass();
            return new DoBillingResults();
        }).when(billingServiceSpy).doBilling(any());
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            Future<ResponseEntity<DoBillingResults>> first = asTenant(held,
                    () -> billingResource.getAllInvoices(params(held, PERIOD)));
            gate.awaitEntered();

            ResponseEntity<DoBillingResults> second = result(asTenant(tenant,
                    () -> billingResource.getAllInvoices(withDate(world.params(PERIOD_TYPE, PERIOD, false)))),
                    "run of the other community while the first is held");

            assertThat(second.getBody().getAbstractText(), is(RESULT_FINAL_OK));
            assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042"));
            gate.release();
            result(first, "held run");
        });
    }

    /**
     * Two real runs (two periods) of one community started at the same moment through the resource: the
     * lock puts them one after the other, the numbers are distinct, the caller sees no error. (Green today:
     * with only two runs the old monitor still serialises them; the overlap needs a third run, see above.)
     */
    @Test
    void twoRunsOfOneCommunityAtOnceGetDistinctNumbers() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        CyclicBarrier start = new CyclicBarrier(2);

        assertTimeoutPreemptively(WHOLE_TEST, () -> {
            List<Future<ResponseEntity<DoBillingResults>>> runs = List.of("Abr_YQ-2024-2", "Abr_YQ-2024-3").stream()
                    .map(period -> asTenant(tenant, () -> {
                        DoBillingParams params = withDate(world.params(PERIOD_TYPE, period, false));
                        assertThat("both runs started", Gate.meet(start, Gate.TIMEOUT), is(true));
                        return billingResource.getAllInvoices(params);
                    }))
                    .toList();

            for (Future<ResponseEntity<DoBillingResults>> run : runs) {
                assertThat(result(run, "run").getBody().getAbstractText(), is(RESULT_FINAL_OK));
            }
            assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042", "TRECH202400043"));
        });
    }

    /**
     * F4 (#15): the number is read (max + 1) and saved without a lock. Two runs of one community in the
     * service (where a second instance of the service would call it as well) both read the maximum before
     * either saves. Correct: distinct numbers and both runs succeed. With the defect both take 42 and the
     * second fails on the unique constraint (tenant, document number).
     */
    @Test
    @Disabled("known-errors #15")
    void numbersOfParallelRunsInTheServiceAreDistinct() {
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

            for (Future<DoBillingResults> run : runs) {
                assertThat(result(run, "run").getAbstractText(), is(RESULT_FINAL_OK));
            }
            assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042", "TRECH202400043"));
        });
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Stubs doBilling: count who is inside, stop at the gate named by the period, return an empty result. */
    private void holdRunsAt(Map<String, Gate> gates) {
        doAnswer(invocation -> {
            DoBillingParams params = invocation.getArgument(0);
            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
            try {
                gates.get(params.getClearingPeriodIdentifier()).pass();
            } finally {
                inside.decrementAndGet();
            }
            return new DoBillingResults();
        }).when(billingServiceSpy).doBilling(any());
    }

    private Future<ResponseEntity<DoBillingResults>> startRun(String tenantId, String name,
                                                              ThreadLocal<String> runName) {
        return asTenant(tenantId, () -> {
            runName.set(name);
            try {
                return billingResource.getAllInvoices(params(tenantId, name));
            } finally {
                runName.remove();
            }
        });
    }

    private static DoBillingParams params(String tenantId, String period) {
        DoBillingParams params = BillingRunFixture.forTenant(tenantId).params(PERIOD_TYPE, period, false);
        return withDate(params);
    }

    private static DoBillingParams withDate(DoBillingParams params) {
        params.setClearingDocumentDate(REFERENCE_DATE);
        return params;
    }
}
