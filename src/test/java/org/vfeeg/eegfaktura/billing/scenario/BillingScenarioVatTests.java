package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;

/** Scenarios S1, S3, S4, S5: VAT rates on one invoice. Amounts are computed by hand in the comments. */
class BillingScenarioVatTests extends BillingScenarioBase {

    /**
     * S1 — two consumers, one VAT rate (20 %), final run.
     * P1: 100.00 kWh × 10 ct = 10.00 + 2.00 VAT; fee 5.00 + 1.00 → net 15.00, VAT 3.00, gross 18.00.
     * P2: 250.50 kWh × 10 ct = 25.05 + 5.01 VAT; fee 5.00 + 1.00 → net 30.05, VAT 6.01, gross 36.06.
     */
    @Test
    void s01_consumersOnlyWithOneVatRate() {
        BillingRunFixture world = insert(oneRateWorld(), false);

        DoBillingResults results = bill(world, false);

        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        assertThat(results.getParticipantAmounts(), hasSize(2));
        assertThat(billingRunService.get(results.getBillingRunId()).getRunStatus(), is(BillingRunStatus.DONE));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs, hasSize(2));
        assertThat(docs.stream().map(BillingDocumentDTO::getBillingDocumentType).toList(), contains(INVOICE, INVOICE));

        BillingDocumentDTO p1 = document(docs, 1, INVOICE);
        assertAmounts(p1, "15.00", "20", "3.00", null, null, "18.00");
        assertThat(p1.getDocumentDate(), is(REFERENCE_DATE));
        List<BillingDocumentItem> p1Items = items(p1.getId());
        assertThat(p1Items, hasSize(2));
        assertItem(p1Items.get(0), "100.00", "10.00", "20", "2.00", "12.00");
        assertItem(p1Items.get(1), "1", "5.00", "20", "1.00", "6.00");
        assertThat(p1Items.get(1).getText(), is("Mitgliedsgebühr"));

        BillingDocumentDTO p2 = document(docs, 2, INVOICE);
        assertAmounts(p2, "30.05", "20", "6.01", null, null, "36.06");

        assertThat(docs.stream().map(BillingDocumentDTO::getDocumentNumber).toList(),
                containsInAnyOrder("TRECH202400042", "TRECH202400043"));
        assertThat(documentNumbers(), contains("TRECH202400042", "TRECH202400043"));
    }

    /**
     * S3 — two VAT rates on one invoice: energy 20 % (100 kWh × 10 ct = 10.00, VAT 2.00), meter-point fee
     * 2.00 with the consumer tariff's 20 % (VAT 0.40), participant fee 10.00 at 10 % (VAT 1.00).
     * Net 22.00, VAT 20 % = 2.40, VAT 10 % = 1.00, gross 25.40.
     */
    @Test
    void s03_twoVatRatesGiveTwoVatSums() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").tariffVat("20")
                .participantFee("10", "10").meteringPointFee("2.00", "ZP", "0"), "100"), false);

        DoBillingResults results = bill(world, true);

        assertThat(results.getAbstractText(), is(RESULT_PREVIEW_OK));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs, hasSize(1));
        BillingDocumentDTO invoice = document(docs, 1, INVOICE);
        assertAmounts(invoice, "22.00", "20", "2.40", "10", "1.00", "25.40");
        assertThat(invoice.getDocumentNumber(), nullValue());
        List<BillingDocumentItem> items = items(invoice.getId());
        assertThat(items, hasSize(3));
        assertItem(items.get(0), "100.00", "10.00", "20", "2.00", "12.00");
        assertItem(items.get(1), "1", "10.00", "10", "1.00", "11.00");
        assertItem(items.get(2), "1", "2.00", "20", "0.40", "2.40");
        assertThat(items.get(2).getText(), is("Zählpunktgebühr: C1"));
        assertThat(documentNumbers(), is(empty()));
    }

    /**
     * S4 — a third VAT rate (producer meter-point fee at 13 %) is not supported. The run must fail and leave
     * nothing behind: no documents, no items, no consumed numbers, no completed run — also for the healthy
     * second participant (known-errors #12, F1: today the partial data are committed).
     */
    @Test
    @Disabled("known-errors #12")
    void s04_threeVatRatesFailAndLeaveNothingBehind() {
        BillingRunFixture world = insert(threeRateWorld(), false);

        DoBillingResults results = bill(world, false);

        assertThat(results.getAbstractText(), startsWith(RESULT_FAILED));
        assertThat(count("billing_document where tenant_id = ?"), is(0));
        assertThat(count("billing_document_item i join billing_document d on d.id = i.billing_document_id"
                + " where d.tenant_id = ?"), is(0));
        assertThat(count("billing_document_file where tenant_id = ?"), is(0));
        assertThat(documentNumbers(), is(empty()));
        assertThat(count("billing_run where tenant_id = ? and run_status = 1"), is(0));
    }

    /** S4 without the defect part: the failure is reported in the result text (holds today). */
    @Test
    void s04_threeVatRatesAreReportedInTheResultText() {
        BillingRunFixture world = insert(threeRateWorld(), false);

        DoBillingResults results = bill(world, true);

        assertThat(results.getAbstractText(), startsWith(RESULT_FAILED + "More than 2 VAT rates not supported"));
        assertThat(results.getBillingRunId(), notNullValue());
    }

    /**
     * S5 — the same rate with a different scale (tariff 20, participant fee 20.00) is one rate: one VAT sum
     * of 4.00 (energy 2.00 + fee 2.00), no second rate (known-errors #16, F5: {@code BigDecimal.equals}).
     */
    @Test
    @Disabled("known-errors #16")
    void s05_sameRateWithOtherScaleIsOneVatSum() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1").tariffVat("20")
                .participantFee("10", "20.00"), "100"), false);

        DoBillingResults results = bill(world, true);

        assertThat(results.getAbstractText(), is(RESULT_PREVIEW_OK));
        BillingDocumentDTO invoice = document(documents(results.getBillingRunId()), 1, INVOICE);
        assertAmounts(invoice, "20.00", "20", "4.00", null, null, "24.00");
    }

    private BillingRunFixture oneRateWorld() {
        return world()
                .master(consumer(1, "C1").tariffVat("20").participantFee("5", "20"), "100")
                .master(consumer(2, "C2").tariffVat("20").participantFee("5", "20"), "250.50");
    }

    private BillingRunFixture threeRateWorld() {
        return world()
                .master(consumer(1, "C1").tariffVat("20").participantFee("10", "10"), "100")
                .master(producer(1, "P1").participantFee("10", "10").meteringPointFee("2.00", "ZP", "13"), "50")
                .master(consumer(2, "C2"), "100");
    }
}
