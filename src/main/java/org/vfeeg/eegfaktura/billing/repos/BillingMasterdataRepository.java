package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vfeeg.eegfaktura.billing.domain.BillingMasterdata;

import java.util.List;
import java.util.Optional;

public interface BillingMasterdataRepository extends JpaRepository<BillingMasterdata, String> {
    List<BillingMasterdata> findByTenantId(String tenantId);

    /**
     * One row of the tenant: the EEG (eec_*) columns are the same in every row. Case-insensitive:
     * the stored tenant case need not match the request header.
     */
    Optional<BillingMasterdata> findFirstByTenantIdIgnoreCase(String tenantId);
}
