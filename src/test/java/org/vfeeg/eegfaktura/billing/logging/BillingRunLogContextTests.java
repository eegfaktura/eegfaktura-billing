package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.vfeeg.eegfaktura.billing.config.AsyncConfig;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The billing run outlives the request that started it, on a thread of its own, through the real {@code @Async} proxy:
 * its lines still carry {@code request.id} and {@code tenant.id}.
 */
@SpringJUnitConfig({AsyncConfig.class, BillingRunLauncher.class})
class BillingRunLogContextTests {

    private static final UUID RUN = UUID.fromString("1f0c2a64-7a0e-4c55-9d7a-2b1c3d4e5f60");

    @Autowired
    BillingRunLauncher launcher;
    @MockitoBean
    BillingService billingService;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(BillingRunLauncher.class);

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
        MDC.put(LogFields.REQUEST_ID, "req-9");
        MDC.put(LogFields.TENANT_ID, "RC100001");
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void failedRunLogsWithTheIdsOfItsRequest() {
        CountDownLatch requestEnded = new CountDownLatch(1);
        Mockito.doAnswer(call -> {
            requestEnded.await(5, TimeUnit.SECONDS);
            throw new IllegalStateException("run failed");
        }).when(billingService).executeBillingRun(eq(RUN), any());

        launcher.launch(RUN, null);
        MDC.clear(); // RequestLogFilter at the end of the request
        requestEnded.countDown();

        verify(billingService, timeout(5000)).markBillingRunFailed(eq(RUN), any());
        ILoggingEvent line = appender.list.get(0);
        assertNotEquals(Thread.currentThread().getName(), line.getThreadName());
        assertTrue(line.getThreadName().startsWith("billing-run-"), line.getThreadName());
        assertEquals("req-9", line.getMDCPropertyMap().get(LogFields.REQUEST_ID));
        assertEquals("RC100001", line.getMDCPropertyMap().get(LogFields.TENANT_ID));
    }
}
