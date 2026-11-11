package org.vfeeg.eegfaktura.billing.repos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingMailTemplateRepository extends JpaRepository<BillingMailTemplate, UUID> {

    Optional<BillingMailTemplate> findByTenantIdAndMailKey(String tenantId, String mailKey);

    List<BillingMailTemplate> findByTenantId(String tenantId);

    /** Row lock for PUT: two concurrent saves with the same version must not both win. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BillingMailTemplate t where t.tenantId = :tenantId and t.mailKey = :mailKey")
    Optional<BillingMailTemplate> lockByTenantIdAndMailKey(@Param("tenantId") String tenantId,
                                                           @Param("mailKey") String mailKey);
}
