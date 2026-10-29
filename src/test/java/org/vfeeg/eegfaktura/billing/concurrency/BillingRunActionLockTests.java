package org.vfeeg.eegfaktura.billing.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.rest.BillingRunResource;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingRunActionLock;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * known-errors #14: export, mail run and deletion of a billing run take a database lock per run (replaces the
 * per-tenant {@code InMemoryLockRepository}, whose lock let a third caller in next to a waiting second one). While
 * one action holds it, a second one on the same run is refused at once with 409; when the holder ends, the next one
 * gets in; other runs are not held up. Real PostgreSQL, the holder in its own thread.
 */
class BillingRunActionLockTests extends BillingScenarioBase {

    private static final long WAIT_SECONDS = 30;

    @Autowired
    private BillingRunActionLock lock;
    @Autowired
    private BillingRunResource billingRunResource;

    private final ExecutorService pool = Executors.newFixedThreadPool(1);
    private UUID run = UUID.randomUUID();

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    /** A holds the run's lock; B (both kinds) is refused, not queued; after A, C gets in. */
    @Test
    void aSecondActionOnTheSameRunIsRefusedAndTheNextOneGetsIn() throws Exception {
        Holder a = hold(false);

        assertBusy(() -> lock.inTransaction(run, () -> "B"));
        assertBusy(() -> lock.onOwnConnection(run, () -> "B"));

        a.end();
        assertThat(lock.inTransaction(run, () -> "C"), is("C"));
    }

    /** The mail run holds the lock on its own connection, and its work runs outside a transaction. */
    @Test
    void theMailRunHoldsTheLockOutsideATransaction() throws Exception {
        Holder a = hold(true);

        assertThat("work of the mail run inside a transaction", a.inTransaction, is(false));
        assertBusy(() -> lock.inTransaction(run, () -> "B"));

        a.end();
        assertThat(lock.onOwnConnection(run, () -> "C"), is("C"));
    }

    @Test
    void anotherRunIsNotHeldUp() throws Exception {
        Holder a = hold(false);

        assertThat(lock.inTransaction(UUID.randomUUID(), () -> "other run"), is("other run"));
        assertThat(lock.onOwnConnection(UUID.randomUUID(), () -> "other run"), is("other run"));
        a.end();
    }

    /**
     * Through the real resource (transaction joining, Spring proxies): a deletion while an export of the run holds
     * the lock is refused with 409 and the run stays; afterwards it is deleted.
     */
    @Test
    void theResourceRefusesADeletionWhileTheRunIsBusy() throws Exception {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        DoBillingParams params = world.params(PERIOD_TYPE, PERIOD, true);
        params.setClearingDocumentDate(REFERENCE_DATE);
        run = billingService.acceptBillingRun(params).getId();
        TenantContext.setCurrentTenant(new Authority(tenant));
        try {
            Holder a = hold(false);
            assertBusy(() -> billingRunResource.deleteBillingRun(run));
            assertBusy(() -> billingRunResource.sendAllBillingDocuments(run));
            assertThat(count("billing_run where tenant_id = ?"), is(1));
            a.end();

            assertThat(billingRunResource.deleteBillingRun(run).getStatusCode(), is(HttpStatus.NO_CONTENT));
            assertThat(count("billing_run where tenant_id = ?"), is(0));
        } finally {
            TenantContext.setCurrentTenant(null);
        }
    }

    /** A failing action releases the lock (end of its transaction). */
    @Test
    void aFailedActionReleasesTheLock() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> lock.inTransaction(run, () -> { throw new IllegalStateException("export failed"); }));
        assertThrows(IllegalStateException.class,
                () -> lock.onOwnConnection(run, () -> { throw new IllegalStateException("mail failed"); }));

        assertThat(lock.inTransaction(run, () -> "next"), is("next"));
    }

    private void assertBusy(org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException refused = assertThrows(ResponseStatusException.class, action);
        assertThat(refused.getStatusCode(), is(HttpStatus.CONFLICT));
    }

    /** Starts an action on {@link #run} in the pool that holds the lock until {@link #end()}. */
    private Holder hold(boolean ownConnection) throws Exception {
        Holder holder = new Holder();
        BillingRunActionLock.Action<Void> work = () -> {
            holder.inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            holder.held.countDown();
            try {
                holder.release.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };
        holder.future = pool.submit(() -> ownConnection ? lock.onOwnConnection(run, work) : lock.inTransaction(run, work));
        assertThat("holder took the lock", holder.held.await(WAIT_SECONDS, TimeUnit.SECONDS), is(true));
        return holder;
    }

    private static class Holder {
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean inTransaction;
        Future<?> future;

        void end() throws Exception {
            release.countDown();
            future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }
}
