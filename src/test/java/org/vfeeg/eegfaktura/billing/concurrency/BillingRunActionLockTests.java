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
 * known-errors #14: export, mail run and deletion of a billing run take database locks per run (replaces the
 * per-tenant {@code InMemoryLockRepository}, whose lock let a third caller in next to a waiting second one). Exports
 * only read and run next to each other and next to a mail run; a second mail run of the run and a deletion during any
 * action (or an action during a deletion) are refused at once with 409; when the holder ends, the next one gets in;
 * other runs are not held up. Real PostgreSQL, the holder in its own thread.
 */
class BillingRunActionLockTests extends BillingScenarioBase {

    private static final long WAIT_SECONDS = 30;

    private enum Kind { EXPORT, DELETE, MAIL }

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

    @Test
    void exportsRunNextToEachOtherAndNextToAMailRun() throws Exception {
        Holder export = hold(Kind.EXPORT);
        assertThat(act(Kind.EXPORT, run), is("EXPORT"));
        assertThat(act(Kind.MAIL, run), is("MAIL"));
        export.end();

        Holder mail = hold(Kind.MAIL);
        assertThat(act(Kind.EXPORT, run), is("EXPORT"));
        mail.end();
    }

    /** A second mail run of the same run is refused, not queued; after the first, the next one gets in. */
    @Test
    void aSecondMailRunIsRefusedAndTheNextOneGetsIn() throws Exception {
        Holder mail = hold(Kind.MAIL);
        assertBusy(Kind.MAIL);
        mail.end();

        assertThat(act(Kind.MAIL, run), is("MAIL"));
    }

    @Test
    void aDeletionIsRefusedDuringAnExportOrAMailRun() throws Exception {
        for (Kind holder : new Kind[]{Kind.EXPORT, Kind.MAIL}) {
            Holder busy = hold(holder);
            assertBusy(Kind.DELETE);
            busy.end();
        }
        assertThat(act(Kind.DELETE, run), is("DELETE"));
    }

    @Test
    void everyActionIsRefusedDuringADeletion() throws Exception {
        Holder delete = hold(Kind.DELETE);
        assertBusy(Kind.EXPORT);
        assertBusy(Kind.MAIL);
        assertBusy(Kind.DELETE);
        delete.end();

        assertThat(act(Kind.EXPORT, run), is("EXPORT"));
    }

    /** The mail run holds its locks on a connection of its own; its work runs outside a transaction. */
    @Test
    void theMailRunWorksOutsideATransaction() throws Exception {
        Holder mail = hold(Kind.MAIL);
        assertThat("work of the mail run inside a transaction", mail.inTransaction, is(false));
        mail.end();
    }

    @Test
    void anotherRunIsNotHeldUp() throws Exception {
        Holder delete = hold(Kind.DELETE);
        for (Kind kind : Kind.values()) {
            assertThat(act(kind, UUID.randomUUID()), is(kind.name()));
        }
        delete.end();
    }

    /**
     * Through the real resource (transaction joining, Spring proxies): a deletion while a mail run of the run holds
     * the locks is refused with 409 and the run stays; afterwards it is deleted.
     */
    @Test
    void theResourceRefusesADeletionWhileTheRunIsBusy() throws Exception {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        DoBillingParams params = world.params(PERIOD_TYPE, PERIOD, true);
        params.setClearingDocumentDate(REFERENCE_DATE);
        run = billingService.acceptBillingRun(params).getId();
        TenantContext.setCurrentTenant(new Authority(tenant));
        try {
            Holder mail = hold(Kind.MAIL);
            assertBusy(() -> billingRunResource.deleteBillingRun(run));
            assertBusy(() -> billingRunResource.sendAllBillingDocuments(run));
            assertThat(count("billing_run where tenant_id = ?"), is(1));
            mail.end();

            assertThat(billingRunResource.deleteBillingRun(run).getStatusCode(), is(HttpStatus.NO_CONTENT));
            assertThat(count("billing_run where tenant_id = ?"), is(0));
        } finally {
            TenantContext.setCurrentTenant(null);
        }
    }

    /** A failing action releases its locks. */
    @Test
    void aFailedActionReleasesTheLock() throws Exception {
        for (Kind kind : Kind.values()) {
            assertThrows(IllegalStateException.class,
                    () -> call(kind, run, () -> { throw new IllegalStateException(kind + " failed"); }));
        }

        assertThat(act(Kind.DELETE, run), is("DELETE"));
        assertThat(act(Kind.MAIL, run), is("MAIL"));
    }

    private String act(Kind kind, UUID billingRunId) throws Exception {
        return call(kind, billingRunId, kind::name);
    }

    private <T> T call(Kind kind, UUID billingRunId, BillingRunActionLock.Action<T> action) throws Exception {
        return switch (kind) {
            case EXPORT -> lock.export(billingRunId, action);
            case DELETE -> lock.delete(billingRunId, action);
            case MAIL -> lock.mailRun(billingRunId, action);
        };
    }

    private void assertBusy(Kind kind) {
        assertBusy(() -> act(kind, run));
    }

    private void assertBusy(org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException refused = assertThrows(ResponseStatusException.class, action);
        assertThat(refused.getStatusCode(), is(HttpStatus.CONFLICT));
    }

    /** Starts an action of {@code kind} on {@link #run} in the pool that holds its locks until {@link Holder#end()}. */
    private Holder hold(Kind kind) throws Exception {
        Holder holder = new Holder();
        UUID billingRunId = run;
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
        holder.future = pool.submit(() -> call(kind, billingRunId, work));
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
