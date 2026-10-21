package org.vfeeg.eegfaktura.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor fuer asynchrone Abrechnungslaeufe. Bewusst klein und ohne Queue:
 * RUNNING bedeutet immer "ein Thread arbeitet wirklich". Ist der Pool voll,
 * lehnt der Executor ab (AbortPolicy) - der Aufrufer rollt den Status-Claim
 * zurueck und antwortet 503 (siehe konzept-async-billing-run.md).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String BILLING_RUN_EXECUTOR = "billingRunExecutor";

    @Bean(name = BILLING_RUN_EXECUTOR)
    public ThreadPoolTaskExecutor billingRunExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("billing-run-");
        // Default RejectedExecutionHandler ist AbortPolicy -> TaskRejectedException beim Submit
        executor.initialize();
        return executor;
    }
}
