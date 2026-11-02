package org.vfeeg.eegfaktura.billing.support;

import java.time.Duration;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A point where a worker thread stops until the test lets it go (M5 concurrency tests). The worker calls
 * {@link #pass()}: it signals {@link #awaitEntered()} and waits for {@link #release()}. Every wait is
 * bounded by {@link #TIMEOUT} and fails with a message; there is no sleep anywhere.
 */
public final class Gate {

    /** Upper bound of every wait that must succeed. */
    public static final Duration TIMEOUT = Duration.ofSeconds(20);
    /**
     * Bounded wait for something that happens at once with the defect and never with the fix (a second
     * thread entering a section that should be locked). Spent only once the defect is fixed.
     */
    public static final Duration NOT_EXPECTED = Duration.ofSeconds(1);

    private final String name;
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    public Gate(String name) {
        this.name = name;
    }

    /** Called by the worker: announce arrival, then wait for the test. */
    public void pass() {
        entered.countDown();
        await(released, name + " released");
    }

    public void awaitEntered() {
        await(entered, name + " entered");
    }

    /** True if the worker arrived within {@link #NOT_EXPECTED}; the test asserts on the result. */
    public boolean enteredWithin(Duration bound) {
        try {
            return entered.await(bound.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for " + name, e);
        }
    }

    public void release() {
        released.countDown();
    }

    public static void await(CountDownLatch latch, String what) {
        try {
            if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("timeout after " + TIMEOUT + " waiting for: " + what);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for: " + what, e);
        }
    }

    /**
     * Waits at the barrier for the other party, at most {@code bound}; returns false if the other party did
     * not come (with the fix it may be blocked behind this one, so going on is the right thing).
     */
    public static boolean meet(CyclicBarrier barrier, Duration bound) {
        try {
            barrier.await(bound.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | BrokenBarrierException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted at the barrier", e);
        }
    }
}
