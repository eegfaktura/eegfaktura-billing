package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vfeeg.eegfaktura.billing.domain.BillingMasterdata;

import java.util.List;

public interface BillingMasterdataRepository extends JpaRepository<BillingMasterdata, String> {
    List<BillingMasterdata> findByTenantId(String tenantId);
}
