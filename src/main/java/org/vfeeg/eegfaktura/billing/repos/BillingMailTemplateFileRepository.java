package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;

import java.util.List;
import java.util.UUID;

public interface BillingMailTemplateFileRepository extends JpaRepository<BillingMailTemplateFile, UUID> {

    List<BillingMailTemplateFile> findByTenantIdAndMailKeyOrderByDateCreated(String tenantId, String mailKey);
}
