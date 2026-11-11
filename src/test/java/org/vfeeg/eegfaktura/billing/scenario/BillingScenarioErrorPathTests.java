package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.service.BillingRunService;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
     * community exists without a run; the year list of the community answers (known-errors #13).
     */
    @Test
    void s08_participantWithAmountZeroLeavesNoDocumentWithoutRun() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1"), "100")
                .master(consumer(2, "C2"), "0"), false);

        DoBillingResults results = bill(world, false);

        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        assertThat(documents(results.getBillingRunId()), hasSize(1));
        assertThat(count("billing_document where tenant_id = ? and billing_run_id is null"), is(0));
        assertThat("nothing unused is stored", count("billing_document where tenant_id = ?"), is(1));
        List<BillingDocumentDTO> year = assertDoesNotThrow(
                () -> billingDocumentService.findByTenantIdAndYear(tenant, REFERENCE_DATE.getYear()));
        assertThat(year, hasSize(1));
    }

    /** #13, same for a preview: the rows of a repeated preview are all replaced, nothing piles up without a run. */
    @Test
    void s08_repeatedPreviewLeavesNoDocumentWithoutRun() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1"), "100")
                .master(producer(2, "P2"), "50")
                .master(consumer(3, "C3"), "0"), false);

        bill(world, true);
        DoBillingResults second = bill(world, true);

        assertThat(count("billing_document where tenant_id = ? and billing_run_id is null"), is(0));
        assertThat(documents(second.getBillingRunId()), hasSize(2));
        assertThat(count("billing_document where tenant_id = ?"), is(2));
    }

    /** #13, an issued document keeps its item and PDF; the run's result stays the same for both participants. */
    @Test
    void s08_issuedDocumentKeepsItemsPdfAndParticipantAmount() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1"), "100")
                .master(producer(1, "P1"), "50")
                .master(consumer(2, "C2"), "0"), false);

        DoBillingResults results = bill(world, false);

        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs, hasSize(2));
        for (BillingDocumentDTO doc : docs) {
            assertThat(items(doc.getId()), hasSize(1));
            assertThat(documentFileRepository.findByBillingDocumentId(doc.getId()), hasSize(1));
        }
        assertThat(results.getParticipantAmounts(), hasSize(2));
        ParticipantAmount billed = results.getParticipantAmounts().stream()
                .filter(amount -> amount.getId().toString().equals(participant(1))).findFirst().orElseThrow();
        assertThat(billed.getAmount().compareTo(new BigDecimal("14.00")), is(0));
        assertThat(billed.getBillingDocumentFileDTOs(), hasSize(2));
    }

    /**
     * #13, a row that an older release left behind stays untouched (no data change) and the year list answers
     * without it; issued documents stay in the list.
     */
    @Test
    void s08_rowWithoutRunFromAnOlderReleaseStaysAndIsNotInTheYearList() {
        BillingRunFixture world = oneConsumer("100");
        bill(world, false);
        UUID leftOver = UUID.randomUUID();
        jdbc.update("insert into billing_document (id, tenant_id, document_date, date_created, last_updated)"
                + " values (?, ?, ?, now(), now())", leftOver, tenant, REFERENCE_DATE);

        List<BillingDocumentDTO> year = assertDoesNotThrow(
                () -> billingDocumentService.findByTenantIdAndYear(tenant, REFERENCE_DATE.getYear()));

        assertThat(year, hasSize(1));
        assertThat(year.get(0).getDocumentNumber(), is("TRECH202400042"));
        assertThat(count("billing_document where tenant_id = ? and billing_run_id is null"), is(1));
        assertThat(assertDoesNotThrow(() -> billingDocumentService.get(leftOver)).getBillingRunId(), nullValue());
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

        // since 1.0.8 the service refuses with an exception (the resource answers 400); nothing is saved
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> bill(world, false, FUTURE));

        assertThat(refused.getMessage(), is("Ungültiges Belegdatum (2999-01-01): Rechnung darf nicht vordatiert werden."));
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

        // since 1.0.8 the service refuses with an exception (the resource answers 409 with the run's id)
        BillingService.BillingRunAlreadyClosedException refused = assertThrows(
                BillingService.BillingRunAlreadyClosedException.class, () -> bill(world, false));

        assertThat(first.getAbstractText(), is(RESULT_FINAL_OK));
        assertThat(refused.getMessage(), startsWith("Abrechnungslauf bereits abgeschlossen"));
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

        BillingService.BillingRunAlreadyClosedException refused = assertThrows(
                BillingService.BillingRunAlreadyClosedException.class, () -> bill(world, true));

        assertThat(refused.getMessage(), containsString("Abrechnungslauf bereits abgeschlossen"));
        assertThat(single(runId).getId(), is(invoice.getId()));
        assertThat(documentNumbers(), contains("TRECH202400042"));
    }

    /**
     * S12 — known-errors #22 (F11): final documents are never deleted (decided 2026-10-31, this replaces the
     * original expectation that a delete removes them): a completed run with documents is refused with 409, not
     * 500, and every row of it stays.
     */
    @Test
    void s12_deletingACompletedRunIsRefusedAndChangesNothing() {
        BillingRunFixture world = oneConsumer("100");
        DoBillingResults results = bill(world, false);
        UUID runId = results.getBillingRunId();
        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        BillingDocumentDTO invoice = single(runId);
        byte[] pdfBefore = pdf(invoice.getId());
        int files = count("file_data where tenant_id = ?");

        assertRefused(runId);

        assertThat(billingRunService.get(runId).getRunStatus(), is(BillingRunStatus.DONE));
        assertThat(single(runId).getId(), is(invoice.getId()));
        assertThat(single(runId).getDocumentNumber(), is("TRECH202400042"));
        assertThat(items(invoice.getId()), hasSize(1));
        assertThat(documentFileRepository.findByBillingRunId(runId), hasSize(1));
        assertThat(pdf(invoice.getId()), is(pdfBefore));
        assertThat(count("file_data where tenant_id = ?"), is(files));
        assertThat(documentNumbers(), contains("TRECH202400042"));
    }

    /** S12 — a preview run goes with everything hanging on it, and with nothing else of the community. */
    @Test
    void s12_deletingAPreviewRunRemovesItsDocumentsAndNothingElse() {
        BillingRunFixture world = oneConsumer("100");
        DoBillingResults done = billingService.doBilling(params(world, "Abr_YQ-2024-1", false));
        UUID previewRun = bill(world, true).getBillingRunId();
        int files = count("file_data where tenant_id = ?");
        assertThat(documents(previewRun), hasSize(1));

        billingRunService.delete(previewRun);

        assertThat(jdbc.queryForObject("select count(*) from billing_run where id = ?", Integer.class, previewRun), is(0));
        assertThat(count("billing_document where tenant_id = ?"), is(1));
        assertThat(count("billing_document_item bdi join billing_document bd on bdi.billing_document_id = bd.id"
                + " where bd.tenant_id = ?"), is(1));
        assertThat(count("billing_document_file where tenant_id = ?"), is(1));
        assertThat(count("file_data where tenant_id = ?"), is(files - 1));
        assertThat(single(done.getBillingRunId()).getDocumentNumber(), is("TRECH202400042"));
        assertThat(documentNumbers(), contains("TRECH202400042"));
    }

    /** S12 — the INFO document of a preview carries "-" as its number: that is no final number. */
    @Test
    void s12_deletingAPreviewRunWithAnInfoDocumentWorks() {
        BillingRunFixture world = insert(world()
                .master(producer(1, "P1").column("participant_vat_id", "ATU12345678"), "50"), false);
        UUID runId = bill(world, true).getBillingRunId();
        assertThat(documents(runId).get(0).getDocumentNumber(), is("-"));

        billingRunService.delete(runId);

        assertThat(count("billing_run where tenant_id = ?"), is(0));
        assertThat(count("billing_document where tenant_id = ?"), is(0));
    }

    /** S12 — a failed run only holds preview documents of an earlier attempt, like a preview run. */
    @Test
    void s12_deletingAFailedRunRemovesItsPreviewDocuments() {
        BillingRunFixture world = oneConsumer("100");
        UUID runId = bill(world, true).getBillingRunId();
        jdbc.update("update billing_run set run_status = ? where id = ?", BillingRunStatus.FAILED.ordinal(), runId);

        billingRunService.delete(runId);

        assertThat(count("billing_run where tenant_id = ?"), is(0));
        assertThat(count("billing_document where tenant_id = ?"), is(0));
    }

    /** S12 — final numbers protect the documents whatever the run status says. */
    @Test
    void s12_runWithAFinalNumberIsRefusedInAnyStatus() {
        BillingRunFixture world = oneConsumer("100");
        UUID runId = bill(world, false).getBillingRunId();
        jdbc.update("update billing_run set run_status = ? where id = ?", BillingRunStatus.NEW.ordinal(), runId);

        assertRefused(runId);

        assertThat(single(runId).getDocumentNumber(), is("TRECH202400042"));
    }

    /** S12 — a RUNNING run is never deleted, with or without documents. */
    @Test
    void s12_runningRunIsRefused() {
        UUID runId = billingRunService.create(run(BillingRunStatus.RUNNING));

        assertRefused(runId);

        assertThat(billingRunService.get(runId).getRunStatus(), is(BillingRunStatus.RUNNING));
    }

    /**
     * S12 — a completed run without documents (platform#189) stays deletable as before: nothing final is lost, and
     * it would otherwise block its period for good.
     */
    @Test
    void s12_completedRunWithoutDocumentsIsDeleted() {
        UUID runId = billingRunService.create(run(BillingRunStatus.DONE));

        billingRunService.delete(runId);

        assertThat(count("billing_run where tenant_id = ?"), is(0));
    }

    private void assertRefused(UUID runId) {
        ResponseStatusException refused = assertThrows(BillingRunService.RunNotDeletableException.class,
                () -> billingRunService.delete(runId));
        assertThat(refused.getStatusCode(), is(HttpStatus.CONFLICT));
    }

    private BillingRunDTO run(BillingRunStatus status) {
        BillingRunDTO run = new BillingRunDTO();
        run.setTenantId(tenant);
        run.setClearingPeriodType(PERIOD_TYPE);
        run.setClearingPeriodIdentifier(PERIOD);
        run.setRunStatus(status);
        return run;
    }

    private DoBillingParams params(BillingRunFixture world, String period, boolean preview) {
        DoBillingParams params = world.params(PERIOD_TYPE, period, preview);
        params.setClearingDocumentDate(REFERENCE_DATE);
        return params;
    }

    private BillingDocumentDTO single(UUID runId) {
        List<BillingDocumentDTO> docs = documents(runId);
        assertThat(docs, hasSize(1));
        assertThat(docs.get(0).getBillingDocumentType(), is(INVOICE));
        return docs.get(0);
    }
}
