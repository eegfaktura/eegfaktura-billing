package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeRecordRequest;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeState;
import org.vfeeg.eegfaktura.billing.service.JoiningFeeService;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.ENERGY;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.JOINING_FEE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.PARTICIPANT_FEE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;

/**
 * Extension of 27.10.2026 (konzept-beitrittsgebuehr.md): charge the joining fee in the next final run
 * („nachverrechnen“) or record it as paid outside eegfaktura; both at most until a fee is billed.
 */
class JoiningFeeRecordTests extends JoiningFeeScenarioBase {

    @Autowired
    private JoiningFeeService joiningFeeService;

    /** An already billed member gets the fee by request; previews show it without consuming the request. */
    @Test
    void requestChargesAnAlreadyBilledMemberInTheNextFinalRun() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        run(world, PERIOD, false);
        jdbc.update("update base.billing_masterdata set tariff_joining_fee = 50 where tenant_id = ?", tenant);
        assertThat(itemsOfType(run(world, Q3, true).getBillingRunId(), JOINING_FEE), is(empty())); // rule 11

        joiningFeeService.saveRecord(tenant, participant(1), request("CHARGE_REQUESTED", null, null), "admin@eeg");

        assertThat(itemsOfType(run(world, Q3, true).getBillingRunId(), JOINING_FEE), hasSize(1));
        assertThat(state(1).isRequested(), is(true));
        assertThat(itemsOfType(run(world, Q3, false).getBillingRunId(), JOINING_FEE), hasSize(1));
        assertThat(state(1).isRequested(), is(false));
        assertThat(state(1).getJoiningFee(), notNullValue());
        assertThat(jdbc.queryForObject("select created_by from billing_joining_fee_record where tenant_id = ?",
                String.class, tenant), is("admin@eeg"));
        assertThat(joiningFeesInFinalRuns(), is(1));
    }

    /**
     * A requested member without energy in the run: in the run with 0 kWh the fee joins the usual invoice (running
     * fee); not in the run at all, the member gets an own invoice with only the fee.
     */
    @Test
    void requestedMembersWithoutEnergyGetTheFeeOnAnInvoice() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1").participantFee("2", null).joiningFee("50", null), "0")
                .master(producer(2, "P2").joiningFee("50", null), null), false);
        joiningFeeService.saveRecord(tenant, participant(1), request("CHARGE_REQUESTED", null, null), null);
        joiningFeeService.saveRecord(tenant, participant(2), request("CHARGE_REQUESTED", null, null), null);

        DoBillingResults results = run(world, PERIOD, false, AllocationBuilder.of(participant(1), "C1", "0"));

        assertThat(types(itemsOf(results.getBillingRunId(), 1, INVOICE)), contains(JOINING_FEE, PARTICIPANT_FEE));
        List<BillingDocumentItem> own = itemsOf(results.getBillingRunId(), 2, INVOICE);
        assertThat(types(own), contains(JOINING_FEE));
        assertItem(own.get(0), "1", "50.00", "0", "0", "50.00");
        assertThat(documents(results.getBillingRunId()), hasSize(2));
        assertThat(results.getParticipantAmounts(), hasSize(2));
    }

    /** Deleting the final run with the requested fee: due again, the request stays. */
    @Test
    void deletedRunKeepsTheRequest() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        run(world, PERIOD, false);
        jdbc.update("update base.billing_masterdata set tariff_joining_fee = 50 where tenant_id = ?", tenant);
        joiningFeeService.saveRecord(tenant, participant(1), request("CHARGE_REQUESTED", null, null), null);
        DoBillingResults charged = run(world, Q3, false);
        assertThat(state(1).isRequested(), is(false));

        deleteRun(charged.getBillingRunId());

        assertThat(state(1).isRequested(), is(true));
        DoBillingResults again = run(world, "Abr_YQ-2024-4", false);
        assertThat(itemsOfType(again.getBillingRunId(), JOINING_FEE), hasSize(1));
        assertThat(joiningFeesInFinalRuns(), is(1));
    }

    /** Paid outside eegfaktura: no automatic fee with the first energy billing; the state shows date and note. */
    @Test
    void paidExternallyPreventsTheAutomaticFee() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        joiningFeeService.saveRecord(tenant, participant(1),
                request("PAID_EXTERNALLY", REFERENCE_DATE.toString(), "  bar beim Beitritt "), null);

        DoBillingResults results = run(world, PERIOD, false);

        assertThat(types(itemsOf(results.getBillingRunId(), 1, INVOICE)), contains(ENERGY));
        JoiningFeeState state = state(1);
        assertThat(state.getPaidExternally().getPaidOn(), is(REFERENCE_DATE));
        assertThat(state.getPaidExternally().getNote(), is("bar beim Beitritt"));
        assertThat(state.getJoiningFee(), nullValue());
    }

    /** One record per member: PAID_EXTERNALLY replaces a request (and back); DELETE removes it. */
    @Test
    void theTwoKindsExcludeEachOther() {
        insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        joiningFeeService.saveRecord(tenant, participant(1), request("CHARGE_REQUESTED", null, null), null);
        joiningFeeService.saveRecord(tenant, participant(1), request("PAID_EXTERNALLY", "2024-01-02", null), null);

        assertThat(state(1).isRequested(), is(false));
        assertThat(state(1).getPaidExternally().getPaidOn(), is(LocalDate.of(2024, 1, 2)));
        assertThat(count("billing_joining_fee_record where tenant_id = ?"), is(1));

        joiningFeeService.deleteRecord(tenant, participant(1));
        joiningFeeService.deleteRecord(tenant, participant(1)); // idempotent
        assertThat(joiningFeeService.findJoiningFeeState(tenant), is(empty()));
    }

    /** Once a final document carries the fee, neither a record nor taking one back is possible (409). */
    @Test
    void recordsAreRefusedOnceTheFeeIsBilled() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        joiningFeeService.saveRecord(tenant, participant(1), request("CHARGE_REQUESTED", null, null), null);
        run(world, PERIOD, false);

        assertStatus(HttpStatus.CONFLICT, () -> joiningFeeService.saveRecord(tenant, participant(1),
                request("PAID_EXTERNALLY", "2024-01-02", null), null));
        assertStatus(HttpStatus.CONFLICT, () -> joiningFeeService.deleteRecord(tenant, participant(1)));
        // another community's fee does not block this member there
        joiningFeeService.saveRecord(tenant + "X", participant(1), request("CHARGE_REQUESTED", null, null), null);
        jdbc.update("delete from billing_joining_fee_record where tenant_id = ?", tenant + "X");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, NULL, NULL",                      // no kind
            "PAID, NULL, NULL",                      // unknown kind
            "PAID_EXTERNALLY, NULL, NULL",           // payment date missing
            "PAID_EXTERNALLY, 02.01.2024, NULL",     // not ISO
            "PAID_EXTERNALLY, 2999-01-01, NULL",     // in the future
            "CHARGE_REQUESTED, 2024-01-02, NULL",    // date of the other kind
            "CHARGE_REQUESTED, NULL, note"})         // note of the other kind
    void invalidBodiesAreBadRequests(String kind, String paidOn, String note) {
        insert(world().master(consumer(1, "C1"), "100"), false);
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> joiningFeeService.saveRecord(tenant, participant(1), request(kind, paidOn, note), null));
        assertThat(count("billing_joining_fee_record where tenant_id = ?"), is(0));
    }

    @Test
    void longNoteAndBadMemberIdAreBadRequests() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> joiningFeeService.saveRecord(tenant, participant(1),
                request("PAID_EXTERNALLY", "2024-01-02", "x".repeat(201)), null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> joiningFeeService.saveRecord(tenant, "no-uuid",
                request("CHARGE_REQUESTED", null, null), null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> joiningFeeService.deleteRecord(tenant, "no-uuid"));
    }

    private JoiningFeeState state(int participant) {
        return joiningFeeService.findJoiningFeeState(tenant).stream()
                .filter(s -> s.getParticipantId().equals(participant(participant))).findFirst()
                .orElseThrow(() -> new AssertionError("no state for participant " + participant));
    }

    private static JoiningFeeRecordRequest request(String kind, String paidOn, String note) {
        JoiningFeeRecordRequest request = new JoiningFeeRecordRequest();
        request.setKind(kind);
        request.setPaidOn(paidOn);
        request.setNote(note);
        return request;
    }

    private static List<?> types(List<BillingDocumentItem> items) {
        return items.stream().map(BillingDocumentItem::getItemType).toList();
    }

    private static void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable call) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, call);
        assertThat(e.getStatusCode().value(), is(status.value()));
    }
}
