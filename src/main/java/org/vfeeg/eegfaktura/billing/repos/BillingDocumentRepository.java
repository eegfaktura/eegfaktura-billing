package org.vfeeg.eegfaktura.billing.repos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;


public interface BillingDocumentRepository extends JpaRepository<BillingDocument, UUID> {

    List<BillingDocument> findByTenantIdAndDocumentDateBetween(String tenantId, LocalDate startDate, LocalDate endDate);
    List<BillingDocument> findByBillingRunId(UUID billingRundId);
    Integer deleteByBillingRunId(UUID billingRunId);

}
