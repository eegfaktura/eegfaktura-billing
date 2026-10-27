package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeState;
import org.vfeeg.eegfaktura.billing.service.JoiningFeeService;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.JOINING_FEE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.PARTICIPANT_FEE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;

/**
 * Joining fee: own VAT setting (AK 8, 10), communities kept apart (AK 16) and the state per member that
 * {@code GET /api/billingDocuments/tenant/{tenantId}/joiningFeeState} returns.
 */
class JoiningFeeVatAndStateTests extends JoiningFeeScenarioBase {

    @Autowired
    private JoiningFeeService joiningFeeService;

    /**
     * AK 8: running fee without VAT, joining fee 20 %, energy 20 %: each item keeps its own rate, one VAT sum.
     * Energy 10.00 + 2.00, fee 10.00, joining fee 50.00 + 10.00 → net 70.00, VAT 12.00, gross 82.00.
     */
    @Test
    void joiningFeeWithVatNextToAFeeWithout() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").tariffVat("20")
                .participantFee("10", null).joiningFee("50", "20"), "100"), false);

        DoBillingResults results = run(world, PERIOD, true);

        BillingDocumentDTO invoice = document(documents(results.getBillingRunId()), 1, INVOICE);
        assertAmounts(invoice, "70.00", "20", "12.00", null, null, "82.00");
        List<BillingDocumentItem> items = items(invoice.getId());
        assertItem(items.get(1), "1", "50.00", "20", "10.00", "60.00");
        assertThat(items.get(1).getItemType(), is(JOINING_FEE));
        assertItem(items.get(2), "1", "10.00", "0", "0", "10.00");
        assertThat(items.get(2).getItemType(), is(PARTICIPANT_FEE));
    }

    /** AK 8, the other way round: running fee 20 %, joining fee „USt nein“ with a rate left (13 %) → 0 %. */
    @Test
    void joiningFeeWithoutVatNextToAFeeWith() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").tariffVat("20")
                .participantFee("10", "20").joiningFee("50", null)
                .column("tariff_joining_fee_vat_in_percent", new java.math.BigDecimal("13")), "100"), false);

        DoBillingResults results = run(world, PERIOD, true);

        BillingDocumentDTO invoice = document(documents(results.getBillingRunId()), 1, INVOICE);
        assertAmounts(invoice, "70.00", "20", "4.00", null, null, "74.00");
        assertItem(items(invoice.getId()).get(1), "1", "50.00", "0", "0", "50.00");
    }

    /** AK 10: a third rate through the joining fee aborts the run naming the member number and the rates. */
    @Test
    void thirdRateNamesTheMemberAndTheRates() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").tariffVat("20")
                .participantFee("10", "10").joiningFee("50", "13").column("participant_number", "0042"), "100"), false);

        BillingService.VatRateLimitException failed =
                assertThrows(BillingService.VatRateLimitException.class, () -> run(world, PERIOD, false));

        assertThat(failed.getMessage(), is("Mitglied 0042: mehr als zwei verschiedene USt-Sätze über 0 % auf einem"
                + " Beleg (20,00 %, 10,00 %, 13,00 %). Bitte die USt-Sätze der Tarife prüfen."));
        assertThat(count("billing_run where tenant_id = ? and run_status = 1"), is(0));
    }

    /**
     * AK 16: the final energy document of another community — a GEA sub-community of the same root with the
     * same member id — does not make the fee paid here.
     */
    @Test
    void documentsOfAnotherCommunityDoNotCount() {
        String own = tenant;
        String sibling = own + "-002";
        tenant = sibling;
        BillingRunFixture other = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        run(other, PERIOD, false);
        try {
            tenant = own;
            BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);

            assertThat(itemsOfType(run(world, PERIOD, false).getBillingRunId(), JOINING_FEE), hasSize(1));
            assertThat(joiningFeeService.findJoiningFeeState(own), hasSize(1));
        } finally {
            tenant = sibling;
            cleanUp();
            tenant = own;
        }
    }

    /**
     * The state per member: member 1 got energy and the fee, member 2 only the running fee (no energy yet),
     * member 3 only previews (absent); another community's members are absent as well.
     */
    @Test
    void stateListsMembersWithFinalDocuments() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1").joiningFee("50", null), "100")
                .master(consumer(2, "C2").participantFee("2", null).joiningFee("50", null), "0")
                .master(consumer(3, "C3"), "100"), false);
        run(world, PERIOD, false, AllocationBuilder.of(participant(1), "C1", "100"),
                AllocationBuilder.of(participant(2), "C2", "0"));
        run(world, Q3, true);
        String number = document(documents(jdbc.queryForObject(
                "select id from billing_run where tenant_id = ? and run_status = 1", java.util.UUID.class, tenant)),
                1, INVOICE).getDocumentNumber();

        List<JoiningFeeState> state = joiningFeeService.findJoiningFeeState(tenant);

        assertThat(state.stream().map(JoiningFeeState::getParticipantId).toList(),
                contains(participant(1), participant(2)));
        assertThat(state.get(0).isEnergyBilled(), is(true));
        assertThat(state.get(0).getJoiningFee().getDocumentNumber(), is(number));
        assertThat(state.get(0).getJoiningFee().getDocumentDate(), is(REFERENCE_DATE));
        assertThat(state.get(1).isEnergyBilled(), is(false));
        assertThat(state.get(1).getJoiningFee(), nullValue());
        assertThat(joiningFeeService.findJoiningFeeState(tenant + "X"), hasSize(0));
    }
}
