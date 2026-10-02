package org.vfeeg.eegfaktura.billing.repos;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * The 15-minute expiry (private constant, no clock) and the cleanup task (private, never called, F18)
 * are not testable without a code change; they wait for M4 (4a/4b).
 */
class InMemoryLockRepositoryTests {

    private static final String TENANT = "TE100100";
    private static final long TIMEOUT_SECONDS = 5;

    private final InMemoryLockRepository repository = new InMemoryLockRepository();

    @Test
    void sameTenantGetsTheSameLock() {
        Object first = repository.getLock(TENANT);

        assertThat(first, is(notNullValue()));
        assertThat(repository.getLock(TENANT), is(sameInstance(first)));
    }

    @Test
    void twoTenantsGetTwoLocks() {
        assertThat(repository.getLock(TENANT), is(not(sameInstance(repository.getLock("RC100001")))));
    }

    @Test
    void releaseRemovesTheLock() {
        Object first = repository.getLock(TENANT);
        Object other = repository.getLock("RC100001");

        repository.releaseLock(TENANT);

        assertThat(repository.getLock(TENANT), is(not(sameInstance(first))));
        assertThat(repository.getLock("RC100001"), is(sameInstance(other)));
    }

    @Test
    void releaseOfAnUnknownKeyIsHarmless() {
        repository.releaseLock("unknown");

        assertThat(repository.getLock("unknown"), is(notNullValue()));
    }

    /**
     * F3: three threads of one tenant use the controller's pattern (getLock, synchronized, releaseLock in
     * finally; BillingResource). A holds the lock, B has fetched the same lock object and waits. When A
     * releases, the entry is removed, so C fetches a new object and runs while B runs. Deterministic: B
     * waits inside its section until C is inside too (bounded latch wait, no sleep); with a correct lock
     * C cannot get in and B's wait simply runs out.
     */
    @Test
    @Disabled("known-errors #14")
    void threeThreadsOfOneTenantNeverOverlap() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        CountDownLatch aInside = new CountDownLatch(1);
        CountDownLatch aMayLeave = new CountDownLatch(1);
        CountDownLatch bHasLock = new CountDownLatch(1);
        CountDownLatch bInside = new CountDownLatch(1);
        CountDownLatch cInside = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> a = pool.submit(() -> runLikeController(inside, maxInside, () -> {
                aInside.countDown();
                await(aMayLeave);
            }));
            await(aInside);

            Future<?> b = pool.submit(() -> {
                Object lock = repository.getLock(TENANT);
                bHasLock.countDown();
                synchronized (lock) {
                    try {
                        enter(inside, maxInside);
                        bInside.countDown();
                        cInside.await(1, TimeUnit.SECONDS);
                        inside.decrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        repository.releaseLock(TENANT);
                    }
                }
            });
            await(bHasLock);
            aMayLeave.countDown();
            a.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            await(bInside);

            Future<?> c = pool.submit(() -> runLikeController(inside, maxInside, cInside::countDown));
            b.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            c.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat("threads of one tenant inside the locked section at the same time", maxInside.get(), is(1));
    }

    private void runLikeController(AtomicInteger inside, AtomicInteger maxInside, Runnable work) {
        Object lock = repository.getLock(TENANT);
        synchronized (lock) {
            try {
                enter(inside, maxInside);
                work.run();
                inside.decrementAndGet();
            } finally {
                repository.releaseLock(TENANT);
            }
        }
    }

    private static void enter(AtomicInteger inside, AtomicInteger maxInside) {
        maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch not reached within " + TIMEOUT_SECONDS + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
