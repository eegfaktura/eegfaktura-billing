package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


public interface BillingRunRepository extends JpaRepository<BillingRun, UUID> {

    List<BillingRun> findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifierAndRunStatus(String tenantId,
                                                                                                String billingPeriodType,
                                                                                                String billingPeriodIdentifier,
                                                                                                BillingRunStatus runStatus);

    List<BillingRun> findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(String tenantId,
                                                                                    String clearingPeriodType,
                                                                                    String clearingPeriodIdentifier);

    /**
     * Atomarer Claim fuer den asynchronen Abrechnungslauf: setzt RUNNING nur,
     * wenn der Lauf aktuell NEW oder FAILED ist (genau ein Gewinner bei
     * konkurrierenden Starts, auch ueber mehrere Replicas).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BillingRun b set b.runStatus = org.vfeeg.eegfaktura.billing.domain.BillingRunStatus.RUNNING, "
            + "b.runStatusDateTime = :now, b.errorSummary = null "
            + "where b.id = :id and b.runStatus in (org.vfeeg.eegfaktura.billing.domain.BillingRunStatus.NEW, "
            + "org.vfeeg.eegfaktura.billing.domain.BillingRunStatus.FAILED)")
    int claimRun(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Setzt einen zuvor geclaimten Lauf (RUNNING) auf den uebergebenen Status
     * zurueck - fuer den Rollback, wenn der Executor den Lauf ablehnt, sowie
     * fuer die FAILED-Markierung aus dem Async-Wrapper.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BillingRun b set b.runStatus = :to, b.runStatusDateTime = :now, b.errorSummary = :errorSummary "
            + "where b.id = :id and b.runStatus = org.vfeeg.eegfaktura.billing.domain.BillingRunStatus.RUNNING")
    int releaseClaim(@Param("id") UUID id, @Param("to") BillingRunStatus to,
                     @Param("errorSummary") String errorSummary, @Param("now") LocalDateTime now);

}
