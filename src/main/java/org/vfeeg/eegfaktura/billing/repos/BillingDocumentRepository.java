package org.vfeeg.eegfaktura.billing.repos;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;


public interface BillingDocumentRepository extends JpaRepository<BillingDocument, UUID> {

    /** Without the rows older releases left without a run (known-errors #13): they are no issued documents. */
    List<BillingDocument> findByTenantIdAndDocumentDateBetweenAndBillingRunIsNotNull(String tenantId,
                                                                                     LocalDate startDate,
                                                                                     LocalDate endDate);
    List<BillingDocument> findByBillingRunId(UUID billingRundId);
    Integer deleteByBillingRunId(UUID billingRunId);
    boolean existsByBillingRunId(UUID billingRunId);
    boolean existsByBillingRunIdAndDocumentNumberIsNotNullAndDocumentNumberNot(UUID billingRunId, String documentNumber);

    // ---- joining fee: history of a community from its final documents (runs DONE) only ----

    // documents carry the tenant as in base.billing_masterdata (upper case); the caller may send any case
    String DONE_DOCUMENTS = "from BillingDocument d join d.billingRun r where d.tenantId = upper(:tenantId)"
            + " and r.runStatus = org.vfeeg.eegfaktura.billing.domain.BillingRunStatus.DONE";

    /** Members with at least one final document of the community. */
    @Query("select distinct d.participantId " + DONE_DOCUMENTS)
    Set<String> findParticipantsWithFinalDocuments(@Param("tenantId") String tenantId);

    /**
     * Members with an energy item on a final document: {@code item_type} ENERGY, or for items without a type
     * (before V1_18, or written by an older image) a metering point and a text that is not a meter-point fee.
     */
    @Query("select distinct d.participantId " + DONE_DOCUMENTS + " and exists (select i.id from BillingDocumentItem i"
            + " where i.billingDocument = d and (i.itemType = org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.ENERGY"
            + " or (i.itemType is null and i.meteringPointId is not null and i.text not like :meteringPointFeePrefix)))")
    Set<String> findEnergyBilledParticipants(@Param("tenantId") String tenantId,
                                             @Param("meteringPointFeePrefix") String meteringPointFeePrefix);

    /** Final documents with a joining-fee item, oldest first. */
    @Query("select d.participantId as participantId, d.documentNumber as documentNumber,"
            + " d.documentDate as documentDate " + DONE_DOCUMENTS + " and exists (select i.id from BillingDocumentItem i"
            + " where i.billingDocument = d"
            + " and i.itemType = org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.JOINING_FEE)"
            + " order by d.documentDate, d.documentNumber")
    List<BilledJoiningFee> findBilledJoiningFees(@Param("tenantId") String tenantId);

    interface BilledJoiningFee {
        String getParticipantId();
        String getDocumentNumber();
        LocalDate getDocumentDate();
    }

}
