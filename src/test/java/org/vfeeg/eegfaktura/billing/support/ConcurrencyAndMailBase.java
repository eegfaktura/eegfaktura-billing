package org.vfeeg.eegfaktura.billing.support;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentNumberRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.InMemoryLockRepository;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentMailService;
import org.vfeeg.eegfaktura.billing.service.EmailService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Base of the M5 concurrency and mail tests: M3's {@link BillingScenarioBase} plus the JVM-wide
 * {@link GreenMailHolder} and the spies the latches hook into. All M5 classes declare the same spies here,
 * so they share one Spring context (one more than M3's). A spy calls the real method unless a test stubs
 * it; Spring resets the stubs after each test.
 *
 * <p>Worker threads come from {@link #pool}; {@link #asTenant} sets {@link TenantContext} in the worker
 * (the resources validate it) and clears it afterwards. No HTTP, no tokens.
 */
public abstract class ConcurrencyAndMailBase extends BillingScenarioBase {

    @MockitoSpyBean
    protected org.vfeeg.eegfaktura.billing.service.BillingService billingServiceSpy;
    @MockitoSpyBean
    protected InMemoryLockRepository lockSpy;
    @MockitoSpyBean
    protected EmailService emailServiceSpy;
    @MockitoSpyBean
    protected BillingRunRepository billingRunRepositorySpy;
    @MockitoSpyBean
    protected BillingDocumentNumberRepository numberRepositorySpy;

    @Autowired
    protected BillingDocumentMailService mailService;
    @Autowired
    protected JavaMailSenderImpl mailSender;

    protected ExecutorService pool;

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        GreenMailHolder.register(registry);
    }

    @BeforeEach
    void startPoolAndPurgeMail() throws Exception {
        pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "m5-worker");
            thread.setDaemon(true);
            return thread;
        });
        GreenMailHolder.GREEN_MAIL.purgeEmailFromAllMailboxes();
        mailSender.setPort(GreenMailHolder.port());
    }

    @AfterEach
    void stopPoolAndRestoreMailSender() {
        pool.shutdownNow();
        mailSender.setPort(GreenMailHolder.port());
    }

    // ---- threads ---------------------------------------------------------------------------------

    /** Runs {@code work} in a worker thread with {@code tenantId} as the request's community. */
    protected <T> Future<T> asTenant(String tenantId, Callable<T> work) {
        return pool.submit(() -> {
            TenantContext.setCurrentTenant(new Authority(tenantId));
            try {
                return work.call();
            } finally {
                TenantContext.setCurrentTenant(null);
            }
        });
    }

    protected static <T> T result(Future<T> future, String what) {
        try {
            return future.get(Gate.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("timeout after " + Gate.TIMEOUT + " waiting for: " + what, e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError(what + " failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for: " + what, e);
        }
    }

    /**
     * Calls the real bean behind a spy from inside a stub. For a Spring Data repository (a JDK proxy)
     * {@code callRealMethod()} fails ("abstract real method"); the spy's default answer delegates to the
     * real bean for every kind of spy.
     */
    protected static Object proceed(Object spy, InvocationOnMock invocation) throws Throwable {
        return Mockito.mockingDetails(spy).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    // ---- data ------------------------------------------------------------------------------------

    /** Members 1..n, consumers with 100 kWh each and their own address; the community mail is the cc. */
    protected BillingRunFixture members(String... emails) {
        BillingRunFixture world = world();
        for (int i = 0; i < emails.length; i++) {
            world.master(consumer(i + 1, "C" + (i + 1))
                    .column("participant_email", emails[i])
                    .column("eec_email", "office@eeg.example"), "100");
        }
        return insert(world, false);
    }

    /** A final run of {@code world}; asserts that it succeeded. */
    protected UUID finalRun(BillingRunFixture world) {
        DoBillingResults results = bill(world, false);
        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        return results.getBillingRunId();
    }

    protected BillingRunDTO run(UUID billingRunId) {
        return billingRunService.get(billingRunId);
    }

    // ---- mail ------------------------------------------------------------------------------------

    /** Messages delivered into the mailbox of one address (GreenMail stores a copy per recipient). */
    protected static MimeMessage[] receivedBy(String address) {
        return GreenMailHolder.GREEN_MAIL
                .findReceivedMessages(user -> user.getEmail().equalsIgnoreCase(address), message -> true)
                .toArray(MimeMessage[]::new);
    }

    /** A local port nobody listens on: connecting to it is refused at once (SMTP server down). */
    protected static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
