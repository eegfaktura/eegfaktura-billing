package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.service.FinalRunLock;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.JOINING_FEE;

/**
 * AK 7 and the wait limit (grill 27.10.2026): final runs of one community run one after the other; a waiting run
 * sees the documents of the first; after {@code app.final-run-lock-timeout} it ends FAILED.
 */
class JoiningFeeFinalRunLockTests extends JoiningFeeScenarioBase {

    private static final long WAIT_SECONDS = 30;

    @Autowired
    private FinalRunLock finalRunLock;
    @Autowired
    private AppProperties appProperties;
    @Autowired
    private BillingRunLauncher billingRunLauncher;
    @Autowired
    private BillingRunRepository billingRunRepository;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Duration configured = Duration.ofMinutes(30);

    @AfterEach
    void stopPool() {
        appProperties.setFinalRunLockTimeout(configured);
        pool.shutdownNow();
    }

    /** AK 7: run B (Q3) waits while run A (Q2) is not committed, then bills no second fee. */
    @Test
    void secondFinalRunWaitsAndBillsTheFeeOnce() throws Exception {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        CountDownLatch aInside = new CountDownLatch(1);
        CountDownLatch commitA = new CountDownLatch(1);

        Future<DoBillingResults> a = pool.submit(() -> inTransaction(() -> {
            DoBillingResults results = run(world, PERIOD, false);
            aInside.countDown();
            await(commitA);
            return results;
        }));
        assertThat("run A computed", aInside.await(WAIT_SECONDS, TimeUnit.SECONDS), is(true));
        Future<DoBillingResults> b = pool.submit(() -> run(world, Q3, false));

        assertThrows(TimeoutException.class, () -> b.get(2, TimeUnit.SECONDS), "run B must wait for run A");
        commitA.countDown();

        assertThat(itemsOfType(a.get(WAIT_SECONDS, TimeUnit.SECONDS).getBillingRunId(), JOINING_FEE), hasSize(1));
        assertThat(itemsOfType(b.get(WAIT_SECONDS, TimeUnit.SECONDS).getBillingRunId(), JOINING_FEE), is(empty()));
        assertThat(joiningFeesInFinalRuns(), is(1));
    }

    /** Previews do not take the lock: a preview finishes while a final run of the community is held. */
    @Test
    void previewRunsBesideAHeldFinalRun() throws Exception {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> inTransaction(() -> {
            finalRunLock.lock(tenant);
            held.countDown();
            await(release);
            return null;
        }));
        assertThat(held.await(WAIT_SECONDS, TimeUnit.SECONDS), is(true));

        DoBillingResults preview = pool.submit(() -> run(world, Q3, true)).get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(itemsOfType(preview.getBillingRunId(), JOINING_FEE), hasSize(1));
        release.countDown();
        holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /** After the wait limit the waiting run ends FAILED with the message of the grill decision (launcher path). */
    @Test
    void waitingLongerThanTheLimitFailsTheRun() throws Exception {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        appProperties.setFinalRunLockTimeout(Duration.ofSeconds(1));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> inTransaction(() -> {
            finalRunLock.lock(tenant.toLowerCase()); // the lock key ignores the case of the tenant
            held.countDown();
            await(release);
            return null;
        }));
        assertThat(held.await(WAIT_SECONDS, TimeUnit.SECONDS), is(true));

        DoBillingParams params = world.params(PERIOD_TYPE, PERIOD, false);
        params.setClearingDocumentDate(REFERENCE_DATE);
        BillingRun accepted = billingService.acceptBillingRun(params);
        assertThat(billingService.claimBillingRun(accepted.getId()), is(true));
        billingRunLauncher.launch(accepted.getId(), params);

        BillingRun failed = awaitFinished(accepted);
        release.countDown();
        holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(failed.getRunStatus(), is(BillingRunStatus.FAILED));
        assertThat(failed.getErrorSummary(),
                is("Abrechnung fehlgeschlagen: Ein anderer Abrechnungslauf dieser Gemeinschaft läuft noch"));
        assertThat(joiningFeesInFinalRuns(), is(0));
    }

    private BillingRun awaitFinished(BillingRun run) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < end) {
            BillingRun current = billingRunRepository.findById(run.getId()).orElseThrow();
            if (current.getRunStatus() != BillingRunStatus.RUNNING) {
                return current;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run still RUNNING after " + WAIT_SECONDS + " s");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("latch not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
