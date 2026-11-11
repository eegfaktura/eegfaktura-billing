package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.ENERGY;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.JOINING_FEE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.METERING_POINT_FEE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentItemType.PARTICIPANT_FEE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.CREDIT_NOTE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;
import static org.vfeeg.eegfaktura.billing.support.DocumentReaders.pdfText;

/**
 * Joining fee (konzept-beitrittsgebuehr.md, acceptance criteria 1–6, 11–15): due with the member's first final
 * run with energy, once per community, on the invoice only. Consumers bill 100 kWh × 10 ct = 10.00, producers
 * 50 kWh × 8 ct = 4.00 credit; no VAT unless a scenario sets it.
 */
class BillingScenarioJoiningFeeTests extends JoiningFeeScenarioBase {

    /** AK 1: energy, joining fee 50.00 and fee 2.00 on the first final invoice; the next run has no joining fee. */
    @Test
    void consumerPaysTheJoiningFeeWithTheFirstFinalRunOnly() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").participantFee("2", null)
                .joiningFee("50", null).meteringPointFee("1.00", "ZP", "0"), "100"), false);

        DoBillingResults first = run(world, PERIOD, false);

        List<BillingDocumentItem> items = itemsOf(first.getBillingRunId(), 1, INVOICE);
        assertThat(items.stream().map(BillingDocumentItem::getItemType).toList(),
                contains(ENERGY, JOINING_FEE, PARTICIPANT_FEE, METERING_POINT_FEE));
        assertItem(items.get(1), "1", "50.00", "0", "0", "50.00");
        assertThat(items.get(1).getText(), is("Beitrittsgebühr"));
        assertThat(items.get(1).getPpuUnit(), is("€"));
        assertThat(items.get(1).getMeteringPointId(), is((String) null));
        BillingDocumentDTO invoice = document(documents(first.getBillingRunId()), 1, INVOICE);
        assertAmounts(invoice, "63.00", null, null, null, null, "63.00");
        assertThat(amountOf(first, 1).getJoiningFee(), comparesEqualTo(new BigDecimal("50.00")));
        assertThat(amountOf(first, 1).getParticipantFee(), comparesEqualTo(new BigDecimal("2.00")));
        assertThat(amountOf(first, 1).getAmount(), comparesEqualTo(new BigDecimal("10.00")));
        String pdf = pdfText(pdf(invoice.getId()));
        assertThat(pdf, containsString("Beitrittsgebühr"));
        assertThat(pdf, containsString("50,00 €"));
        // the stored run returns the same amounts (GET /api/billingRuns/{id}/participantAmounts)
        assertThat(participantAmountService.getParticipantAmountsByBillingRunId(first.getBillingRunId()).get(0)
                .getJoiningFee(), comparesEqualTo(new BigDecimal("50.00")));

        DoBillingResults second = run(world, Q3, false);

        assertThat(itemsOfType(second.getBillingRunId(), JOINING_FEE), is(empty()));
        assertAmounts(document(documents(second.getBillingRunId()), 1, INVOICE), "13.00", null, null, null, null,
                "13.00");
        assertThat(amountOf(second, 1).getJoiningFee(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(joiningFeesInFinalRuns(), is(1));
    }

    /** AK 2 and 14: a pure producer gets the credit note without the fee and an own invoice with it. */
    @Test
    void pureProducerGetsAnOwnInvoiceForTheJoiningFee() {
        BillingRunFixture world = insert(world().master(producer(1, "P1").joiningFee("50", null), "50"), false);

        DoBillingResults first = run(world, PERIOD, false);

        List<BillingDocumentItem> credit = itemsOf(first.getBillingRunId(), 1, CREDIT_NOTE);
        assertThat(credit.stream().map(BillingDocumentItem::getItemType).toList(), contains(ENERGY));
        List<BillingDocumentItem> invoice = itemsOf(first.getBillingRunId(), 1, INVOICE);
        assertThat(invoice, hasSize(1)); // participant fee 0: no 0-€ item
        assertItem(invoice.get(0), "1", "50.00", "0", "0", "50.00");
        assertThat(invoice.get(0).getItemType(), is(JOINING_FEE));

        DoBillingResults second = run(world, Q3, false);

        assertThat(documents(second.getBillingRunId()).stream().map(BillingDocumentDTO::getBillingDocumentType)
                .toList(), contains(CREDIT_NOTE));
    }

    /** AK 3: a run without energy (0 kWh) carries no joining fee, even with the running fee; the next one does. */
    @Test
    void runWithoutEnergyWaitsForTheFirstEnergy() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").participantFee("2", null)
                .joiningFee("50", null), "100"), false);

        DoBillingResults noEnergy = run(world, PERIOD, false, AllocationBuilder.of(participant(1), "C1", "0"));

        assertThat(itemsOf(noEnergy.getBillingRunId(), 1, INVOICE).stream().map(BillingDocumentItem::getItemType)
                .toList(), contains(PARTICIPANT_FEE));

        DoBillingResults withEnergy = run(world, Q3, false);

        assertThat(itemsOfType(withEnergy.getBillingRunId(), JOINING_FEE), hasSize(1));
    }

    /**
     * AK 4 and 11: a member with a final energy document written before the item kind existed (item_type empty)
     * counts as billed — also when the tariff gets the fee only afterwards. A member whose old final document has
     * only a meter-point fee (also with a metering point, text „Zählpunktgebühr…“) does not.
     */
    @Test
    void membersBilledBeforeTheFeeExistedDoNotPayIt() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1"), "100")
                .master(consumer(2, "C2").meteringPointFee("1.00", "ZP", "0"), "0"), false);
        run(world, PERIOD, false);
        forgetItemTypes();
        jdbc.update("update base.billing_masterdata set tariff_joining_fee = 50 where tenant_id = ?", tenant);

        DoBillingResults next = run(world, Q3, false, AllocationBuilder.of(participant(1), "C1", "100"),
                AllocationBuilder.of(participant(2), "C2", "100"));

        List<BillingDocumentItem> fees = itemsOfType(next.getBillingRunId(), JOINING_FEE);
        assertThat(fees, hasSize(1));
        assertThat(fees.get(0).getBillingDocument().getParticipantId(), is(participant(2)));
    }

    /** AK 5 and 15: three previews show the fee (49.90) and consume nothing; the final run bills it once. */
    @Test
    void previewsShowTheFeeAndTheFinalRunBillsItOnce() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("49.90", null), "100"), false);

        for (int i = 0; i < 3; i++) {
            DoBillingResults preview = run(world, PERIOD, true);
            assertThat(amountOf(preview, 1).getJoiningFee(), comparesEqualTo(new BigDecimal("49.90")));
        }
        assertThat(joiningFeesInFinalRuns(), is(0));

        DoBillingResults done = run(world, PERIOD, false);

        assertItem(itemsOfType(done.getBillingRunId(), JOINING_FEE).get(0), "1", "49.90", "0", "0", "49.90");
        assertThat(joiningFeesInFinalRuns(), is(1));
        assertThat(itemsOfType(run(world, Q3, true).getBillingRunId(), JOINING_FEE), is(empty()));
    }

    /** AK 6: after the support deleted the final run, the fee is due again in the next run. */
    @Test
    void deletedFinalRunMakesTheFeeDueAgain() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        deleteRun(run(world, PERIOD, false).getBillingRunId());

        DoBillingResults again = run(world, Q3, false);

        assertThat(itemsOfType(again.getBillingRunId(), JOINING_FEE), hasSize(1));
        assertThat(joiningFeesInFinalRuns(), is(1));
    }

    /** AK 12: a tariff change (other amount) after the fee was billed brings no second fee. */
    @Test
    void tariffChangeAfterTheFeeBringsNoSecondFee() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);
        run(world, PERIOD, false);
        jdbc.update("update base.billing_masterdata set tariff_joining_fee = 80, tariff_id = ? where tenant_id = ?",
                "00000000-0000-0000-0000-0000000000e9", tenant);

        assertThat(itemsOfType(run(world, Q3, false).getBillingRunId(), JOINING_FEE), is(empty()));
        assertThat(joiningFeesInFinalRuns(), is(1));
    }

    /** AK 13: a tariff without a joining fee bills as before — no item, the same sums. */
    @Test
    void tariffWithoutJoiningFeeBillsAsBefore() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").participantFee("5", null), "100"), false);

        DoBillingResults results = run(world, PERIOD, false);

        assertThat(itemsOf(results.getBillingRunId(), 1, INVOICE).stream().map(BillingDocumentItem::getItemType)
                .toList(), contains(ENERGY, PARTICIPANT_FEE));
        assertAmounts(document(documents(results.getBillingRunId()), 1, INVOICE), "15.00", null, null, null, null,
                "15.00");
        assertThat(amountOf(results, 1).getJoiningFee(), comparesEqualTo(BigDecimal.ZERO));
    }

    /** AK 14: running fee 0 and joining fee 50: the first invoice shows energy and the joining fee only. */
    @Test
    void joiningFeeWithoutRunningFee() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").joiningFee("50", null), "100"), false);

        DoBillingResults results = run(world, PERIOD, false);

        assertThat(itemsOf(results.getBillingRunId(), 1, INVOICE).stream().map(BillingDocumentItem::getItemType)
                .toList(), contains(ENERGY, JOINING_FEE));
        assertThat(amountOf(results, 1).getParticipantFee(), comparesEqualTo(BigDecimal.ZERO));
    }
}
