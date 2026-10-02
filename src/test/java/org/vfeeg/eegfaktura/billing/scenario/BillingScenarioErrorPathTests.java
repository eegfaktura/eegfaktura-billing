package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;

/** Scenarios S8, S9, S10, S12: what a run leaves behind, repeated runs and deleting a run. */
class BillingScenarioErrorPathTests extends BillingScenarioBase {

    private static final LocalDate FUTURE = LocalDate.of(2999, 1, 1);

    /** One consumer: 100 kWh × 10 ct = 10.00, no VAT, no fee. */
    private BillingRunFixture oneConsumer(String kwh) {
        return insert(world().master(consumer(1, "C1"), kwh), false);
    }

    /**
     * S8 — a participant whose amount is 0 (0 kWh, no fee) gets no document, and no document of the
     * community exists without a run; the year list of the community answers.
     * Known-errors #13 (F2): every participant's documents are saved before their amounts are known and stay
     * without a run; the year list then fails with an NPE.
     */
    @Test
    @Disabled("known-errors #13")
    void s08_participantWithAmountZeroLeavesNoDocumentWithoutRun() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1"), "100")
                .master(consumer(2, "C2"), "0"), false);

        DoBillingResults results = bill(world, false);

        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        assertThat(documents(results.getBillingRunId()), hasSize(1));
        assertThat(count("billing_document where tenant_id = ? and billing_run_id is null"), is(0));
        List<BillingDocumentDTO> year = assertDoesNotThrow(
                () -> billingDocumentService.findByTenantIdAndYear(tenant, REFERENCE_DATE.getYear()));
        assertThat(year, hasSize(1));
    }

    /** S8 without the defect part: the zero participant gets no document in the run (holds today). */
    @Test
    void s08_participantWithAmountZeroGetsNoDocumentInTheRun() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1"), "100")
                .master(consumer(2, "C2"), "0"), false);

        DoBillingResults results = bill(world, false);

        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        assertThat(results.getParticipantAmounts(), hasSize(2));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs, hasSize(1));
        assertThat(docs.get(0).getParticipantId(), is(participant(1)));
        assertThat(documentNumbers(), contains("TRECH202400042"));
    }

    /**
     * S9a — a document date in the future is refused before anything is written: no run, no document,
     * no number. (Green today: the check runs before the first save, so F1 does not apply here.)
     */
    @Test
    void s09_documentDateInTheFutureIsRefusedAndNothingIsSaved() {
        BillingRunFixture world = oneConsumer("100");

        DoBillingResults results = bill(world, false, FUTURE);

        assertThat(results.getAbstractText(), is(RESULT_FAILED
                + "Ungültiges Belegdatum (2999-01-01): Rechnung darf nicht vordatiert werden."));
        assertThat(results.getBillingRunId(), nullValue());
        assertThat(count("billing_run where tenant_id = ?"), is(0));
        assertThat(count("billing_document where tenant_id = ?"), is(0));
        assertThat(documentNumbers(), is(empty()));
    }

    /**
     * S9b — a second final run of a completed period is refused; the documents, numbers and status of the
     * completed run stay as they were. (Green today: the check runs before the old documents are deleted.)
     */
    @Test
    void s09_secondFinalRunOfACompletedPeriodIsRefusedAndChangesNothing() {
        BillingRunFixture world = oneConsumer("100");
        DoBillingResults first = bill(world, false);
        List<BillingDocumentDTO> before = documents(first.getBillingRunId());
        int documentsOfTenant = count("billing_document where tenant_id = ?");

        DoBillingResults second = bill(world, false);

        assertThat(first.getAbstractText(), is(RESULT_FINAL_OK));
        assertThat(second.getAbstractText(), startsWith(RESULT_FAILED + "Abrechnungslauf bereits abgeschlossen"));
        assertThat(second.getBillingRunId(), is(first.getBillingRunId()));
        assertThat(billingRunService.get(first.getBillingRunId()).getRunStatus(), is(BillingRunStatus.DONE));
        List<BillingDocumentDTO> after = documents(first.getBillingRunId());
        assertThat(after.stream().map(BillingDocumentDTO::getId).toList(),
                is(before.stream().map(BillingDocumentDTO::getId).toList()));
        assertThat(after.get(0).getDocumentNumber(), is("TRECH202400042"));
        assertThat(count("billing_document where tenant_id = ?"), is(documentsOfTenant));
        assertThat(documentNumbers(), contains("TRECH202400042"));
    }

    /**
     * S10 — preview, preview, final, preview: a repeated preview replaces the documents of the run (and their
     * PDFs); numbers are only consumed by the final run; a preview after the final run is refused.
     */
    @Test
    void s10_previewFinalPreviewReplacesDocumentsAndNumbersOnlyWhenFinal() {
        BillingRunFixture world = oneConsumer("100");

        DoBillingResults preview1 = bill(world, true);
        UUID runId = preview1.getBillingRunId();
        BillingDocumentDTO draft1 = single(runId);
        DoBillingResults preview2 = bill(world, true);
        BillingDocumentDTO draft2 = single(runId);

        assertThat(preview1.getAbstractText(), is(RESULT_PREVIEW_OK));
        assertThat(preview2.getAbstractText(), is(RESULT_PREVIEW_OK));
        assertThat(preview2.getBillingRunId(), is(runId));
        assertThat(draft1.getDocumentNumber(), nullValue());
        assertThat(draft2.getId(), not(is(draft1.getId())));
        assertThat(jdbc.queryForObject("select count(*) from billing_document where id = ?", Integer.class,
                draft1.getId()), is(0));
        assertThat(documentFileRepository.findByBillingRunId(runId), hasSize(1));
        assertThat(documentNumbers(), is(empty()));
        assertThat(billingRunService.get(runId).getRunStatus(), is(BillingRunStatus.NEW));

        DoBillingResults fin = bill(world, false);
        BillingDocumentDTO invoice = single(runId);

        assertThat(fin.getAbstractText(), is(RESULT_FINAL_OK));
        assertThat(fin.getBillingRunId(), is(runId));
        assertThat(invoice.getDocumentNumber(), is("TRECH202400042"));
        assertAmounts(invoice, "10.00", null, null, null, null, "10.00");
        assertThat(documentFileRepository.findByBillingRunId(runId), hasSize(1));
        assertThat(documentNumbers(), contains("TRECH202400042"));
        assertThat(billingRunService.get(runId).getRunStatus(), is(BillingRunStatus.DONE));

        DoBillingResults preview3 = bill(world, true);

        assertThat(preview3.getAbstractText(), containsString("Abrechnungslauf bereits abgeschlossen"));
        assertThat(single(runId).getId(), is(invoice.getId()));
        assertThat(documentNumbers(), contains("TRECH202400042"));
    }

    /**
     * S12 — deleting a run after the final billing removes the run with its documents.
     * Known-errors #22 (F11): the foreign key has no cascade, the delete fails.
     */
    @Test
    @Disabled("known-errors #22")
    void s12_deletingACompletedRunRemovesItWithItsDocuments() {
        BillingRunFixture world = oneConsumer("100");
        DoBillingResults results = bill(world, false);
        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));

        assertDoesNotThrow(() -> billingRunService.delete(results.getBillingRunId()));

        assertThat(count("billing_run where tenant_id = ?"), is(0));
        assertThat(documents(results.getBillingRunId()), is(empty()));
    }

    private BillingDocumentDTO single(UUID runId) {
        List<BillingDocumentDTO> docs = documents(runId);
        assertThat(docs, hasSize(1));
        assertThat(docs.get(0).getBillingDocumentType(), is(INVOICE));
        return docs.get(0);
    }
}
