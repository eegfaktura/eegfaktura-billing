package org.vfeeg.eegfaktura.billing.scenario;

import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.service.ParticipantAmountService;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.util.List;
import java.util.UUID;

/** Helpers of the joining-fee scenarios (konzept-beitrittsgebuehr.md): several runs of one community. */
abstract class JoiningFeeScenarioBase extends BillingScenarioBase {

    static final String Q3 = "Abr_YQ-2024-3";

    @Autowired
    protected ParticipantAmountService participantAmountService;

    /** A run of {@code period}; without allocations those of the fixture. */
    protected DoBillingResults run(BillingRunFixture world, String period, boolean preview, Allocation... allocations) {
        DoBillingParams params = world.params(PERIOD_TYPE, period, preview);
        if (allocations.length > 0) {
            params.setAllocations(allocations);
        }
        params.setClearingDocumentDate(REFERENCE_DATE);
        return billingService.doBilling(params);
    }

    /** Joining-fee items of the tenant on documents of final runs (DONE). */
    protected int joiningFeesInFinalRuns() {
        return count("billing_document_item i join billing_document d on d.id = i.billing_document_id"
                + " join billing_run r on r.id = d.billing_run_id"
                + " where d.tenant_id = ? and r.run_status = 1 and i.item_type = 'JOINING_FEE'");
    }

    /** Items of a run's documents with this type. */
    protected List<BillingDocumentItem> itemsOfType(UUID billingRunId, BillingDocumentItemType type) {
        return documents(billingRunId).stream().flatMap(d -> items(d.getId()).stream())
                .filter(i -> i.getItemType() == type).toList();
    }

    /** Items of the run's only document of this participant and type. */
    protected List<BillingDocumentItem> itemsOf(UUID billingRunId, int participant,
                                                BillingDocumentType type) {
        BillingDocumentDTO doc = document(documents(billingRunId), participant, type);
        return items(doc.getId());
    }

    protected static ParticipantAmount amountOf(DoBillingResults results, int participant) {
        return results.getParticipantAmounts().stream()
                .filter(a -> a.getId().toString().equals(participant(participant)))
                .findFirst().orElseThrow(() -> new AssertionError("no amount for participant " + participant));
    }

    /** Clears the item kinds of the tenant's documents: they look like documents written before V1_18. */
    protected void forgetItemTypes() {
        jdbc.update("update billing_document_item set item_type = null where billing_document_id in"
                + " (select id from billing_document where tenant_id = ?)", tenant);
    }

    /** Deletes a run with everything it wrote, as the support guide „Rechnungslauf löschen“ does. */
    protected void deleteRun(UUID billingRunId) {
        String documents = "(select id from billing_document where billing_run_id = ?)";
        jdbc.update("delete from billing_document_item where billing_document_id in " + documents, billingRunId);
        jdbc.update("delete from billing_document_file where billing_document_id in " + documents, billingRunId);
        jdbc.update("delete from billing_document where billing_run_id = ?", billingRunId);
        jdbc.update("delete from billing_run where id = ?", billingRunId);
    }
}
