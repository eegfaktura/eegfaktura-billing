package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeRecordRequest;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.service.FinalRunLock;
import org.vfeeg.eegfaktura.billing.service.JoiningFeeService;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.JOINING_FEE;

/**
 * AK 7 (konzept-beitrittsgebuehr.md, rule 6, decided 28.10.2026): final runs of one community run one at a time; a
 * second final run is refused at once instead of waiting, and the joining fee records cannot change while a final
 * run of the community runs.
 */
class JoiningFeeFinalRunLockTests extends JoiningFeeScenarioBase {

    private static final long WAIT_SECONDS = 30;

    @Autowired
    private FinalRunLock finalRunLock;
    @Autowired
    private BillingRunLauncher billingRunLauncher;
    @Autowired
    private BillingRunRepository billingRunRepository;
    @Autowired
    private JoiningFeeService joiningFeeService;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    /** AK 7: run B (Q3) is refused while run A (Q2) is not committed; the fee is billed once, by A. */
    @Test
    void secondFinalRunIsRefusedWhileTheFirstRuns() throws Exception {
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

        ExecutionException refused = assertThrows(ExecutionException.class,
                () -> pool.submit(() -> run(world, Q3, false)).get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertThat(refused.getCause(), instanceOf(FinalRunLock.FinalRunBusyException.class));
        commitA.countDown();

        assertThat(itemsOfType(a.get(WAIT_SECONDS, TimeUnit.SECONDS).getBillingRunId(), JOINING_FEE), hasSize(1));
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

    /** Through the launcher a refused run ends FAILED with the message for the admin, at once. */
    @Test
    void aRefusedRunEndsFailedWithTheMessage() throws Exception {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
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
        assertThat(failed.getErrorSummary(), is("Abrechnung fehlgeschlagen: Ein endgültiger Abrechnungslauf dieser "
                + "Gemeinschaft läuft gerade – bitte nach dessen Ende erneut starten"));
        assertThat(joiningFeesInFinalRuns(), is(0));
    }

    /**
     * Review finding 28.10.2026: a run reads the records once, so a record saved or removed while it runs would be
     * ignored (fee billed although paid outside, or a withdrawn request billed). Both are refused with 409 instead.
     */
    @Test
    void recordsCannotChangeWhileAFinalRunRuns() throws Exception {
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

        JoiningFeeRecordRequest paid = new JoiningFeeRecordRequest();
        paid.setKind("PAID_EXTERNALLY");
        paid.setPaidOn("2026-01-15");
        ResponseStatusException saved = assertThrows(ResponseStatusException.class,
                () -> joiningFeeService.saveRecord(tenant, participant(1), paid, null));
        assertThat(saved.getStatusCode(), is(HttpStatus.CONFLICT));
        ResponseStatusException removed = assertThrows(ResponseStatusException.class,
                () -> joiningFeeService.deleteRecord(tenant, participant(1)));
        assertThat(removed.getStatusCode(), is(HttpStatus.CONFLICT));

        release.countDown();
        holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
        joiningFeeService.saveRecord(tenant, participant(1), paid, null);
        assertThat(joiningFeeService.findJoiningFeeState(tenant), hasSize(1));
        assertThat(joiningFeesInFinalRuns(), is(0));
        assertThat(itemsOfType(run(world, PERIOD, true).getBillingRunId(), JOINING_FEE), is(empty()));
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
