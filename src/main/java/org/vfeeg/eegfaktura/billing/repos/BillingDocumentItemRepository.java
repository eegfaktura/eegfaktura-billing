package org.vfeeg.eegfaktura.billing.repos;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;

import java.util.List;
import java.util.UUID;

public interface BillingDocumentItemRepository extends JpaRepository<BillingDocumentItem, UUID> {

    @Modifying
    @Transactional
    @Query(value="DELETE FROM billing_document_item bdi WHERE bdi.billing_document_id IN (SELECT bd.id " +
            " FROM billing_document bd WHERE bd.billing_run_id = :billingRunId)", nativeQuery = true)
    Integer deleteByBillingRunId(@Param("billingRunId") UUID billingRunId);

    List<BillingDocumentItem> findByBillingDocument_Id(UUID billingDocumentId);

}
