package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;

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

}
