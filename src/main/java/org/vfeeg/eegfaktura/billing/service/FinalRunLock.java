package org.vfeeg.eegfaktura.billing.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Final runs of one community run one at a time (konzept-beitrittsgebuehr.md, rule 6): a PostgreSQL
 * transaction-level advisory lock per tenant, held until the run's transaction ends - across replicas and
 * restarts, unlike {@code InMemoryLockRepository}. A second final run does not wait (decided 28.10.2026): it ends
 * at once with {@link FinalRunBusyException}, so no billing thread waits and runs of other communities are never
 * held up. The joining fee records of the community take the same lock, so they cannot change while a final run
 * reads them.
 */
@Component
public class FinalRunLock {

    static final String BUSY_MESSAGE =
            "Ein endgültiger Abrechnungslauf dieser Gemeinschaft läuft gerade – bitte nach dessen Ende erneut starten";
    /** first key of the two-key advisory lock: keeps these locks apart from other (single-key) advisory locks */
    private static final int LOCK_CLASS = 217;

    private final EntityManager entityManager;

    public FinalRunLock(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Takes the community's lock without waiting; must run inside a transaction. False: a final run holds it. */
    public boolean tryLock(String tenantId) {
        Object locked = entityManager.createNativeQuery("select pg_try_advisory_xact_lock(?1, hashtext(?2))")
                .setParameter(1, LOCK_CLASS)
                .setParameter(2, "billing_run_final/" + tenantId.toUpperCase(Locale.ROOT))
                .getSingleResult();
        return Boolean.TRUE.equals(locked);
    }

    /** For a final run: the community's lock, or {@link FinalRunBusyException} when another final run holds it. */
    public void lock(String tenantId) {
        if (!tryLock(tenantId)) {
            throw new FinalRunBusyException();
        }
    }

    /** Another final run of the community holds the lock; the launcher records the run as FAILED with this text. */
    public static class FinalRunBusyException extends RuntimeException {
        public FinalRunBusyException() { super(BUSY_MESSAGE); }
    }
}
