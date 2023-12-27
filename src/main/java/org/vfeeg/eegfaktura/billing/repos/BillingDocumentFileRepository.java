package org.vfeeg.eegfaktura.billing.repos;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;

import java.util.List;
import java.util.UUID;


public interface BillingDocumentFileRepository extends JpaRepository<BillingDocumentFile, UUID> {

    List<BillingDocumentFile> findByTenantId(String tenantId);

    List<BillingDocumentFile> findByBillingDocumentId(UUID billingDocumentId);

    @Query(value="SELECT bdf.* FROM billing_document_file bdf, billing_document " +
            " bd WHERE bdf.billing_document_id = bd.id AND bd.billing_run_id = :billingRunId", nativeQuery = true)
    List<BillingDocumentFile> findByBillingRunId(@Param("billingRunId") UUID billingRunId);

    @Modifying
    @Transactional
    @Query(value="DELETE FROM billing_document_file bdf WHERE bdf.billing_document_id IN (SELECT bd.id " +
            " FROM billing_document bd WHERE bd.billing_run_id = :billingRunId)", nativeQuery = true)
    Integer deleteByBillingRunId(@Param("billingRunId") UUID billingRunId);

}
