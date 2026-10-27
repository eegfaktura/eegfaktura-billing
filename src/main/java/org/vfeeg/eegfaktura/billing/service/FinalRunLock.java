package org.vfeeg.eegfaktura.billing.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Component;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Final runs of one community run one after the other (konzept-beitrittsgebuehr.md, rule 6): a PostgreSQL
 * transaction-level advisory lock per tenant, held until the run's transaction ends - across replicas and
 * restarts, unlike {@code InMemoryLockRepository}. A second final run waits (status RUNNING) and then sees the
 * documents of the first. The wait is limited by {@code app.final-run-lock-timeout}
 * ({@code BILLING_FINAL_RUN_LOCK_TIMEOUT}, default 30 minutes, 0 = no limit); after it the run fails.
 */
@Component
public class FinalRunLock {

    static final String WAIT_TIMEOUT_MESSAGE = "Ein anderer Abrechnungslauf dieser Gemeinschaft läuft noch";
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final EntityManager entityManager;
    private final AppProperties appProperties;

    public FinalRunLock(final EntityManager entityManager, final AppProperties appProperties) {
        this.entityManager = entityManager;
        this.appProperties = appProperties;
    }

    /** Waits for the community's lock; must run inside the run's transaction. */
    public void lock(String tenantId) {
        String previousTimeout = (String) entityManager
                .createNativeQuery("select current_setting('lock_timeout')").getSingleResult();
        setLockTimeout(appProperties.getFinalRunLockTimeout().toMillis() + "ms");
        try {
            entityManager.createNativeQuery("select 1 from (select pg_advisory_xact_lock(hashtext(?1))) l")
                    .setParameter(1, "billing_run_final/" + tenantId.toUpperCase(Locale.ROOT))
                    .getSingleResult();
        } catch (PersistenceException e) {
            if (isLockNotAvailable(e)) {
                throw new FinalRunLockTimeoutException();
            }
            throw e;
        }
        // later lock waits of the run (e.g. document numbers) keep the server's setting
        setLockTimeout(previousTimeout);
    }

    private void setLockTimeout(String value) {
        entityManager.createNativeQuery("select set_config('lock_timeout', ?1, true)")
                .setParameter(1, value).getSingleResult();
    }

    private static boolean isLockNotAvailable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /** The wait limit passed; the launcher records the run as FAILED with this message. */
    public static class FinalRunLockTimeoutException extends RuntimeException {
        public FinalRunLockTimeoutException() { super(WAIT_TIMEOUT_MESSAGE); }
    }
}
