package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.BillingMasterdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface BillingConfigRepository extends JpaRepository<BillingConfig, UUID> {

    Optional<BillingConfig> findByTenantId(String tenantId);

    Optional<BillingConfig> findFirstByTenantId(String tenantId);

}
