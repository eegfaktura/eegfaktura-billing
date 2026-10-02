package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;

/** Scenarios S6 (free kWh) and S7 (discount and fees), plus the F15 check of the default configuration. */
class BillingScenarioFeeTests extends BillingScenarioBase {

    /**
     * S6 — free kWh larger than, equal to and smaller than the consumption (100 kWh each, 10 ct, no fee):
     * 150 free → 0 kWh, no item, no invoice; 100 free → the same; 40 free → 60 kWh = 6.00.
     */
    @Test
    void s06_freeKwhAboveEqualAndBelowTheConsumption() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1").freeKwh("150"), "100")
                .master(consumer(2, "C2").freeKwh("100"), "100")
                .master(consumer(3, "C3").freeKwh("40"), "100"), false);

        DoBillingResults results = bill(world, true);

        assertThat(results.getAbstractText(), is(RESULT_PREVIEW_OK));
        assertThat(results.getParticipantAmounts(), hasSize(3));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat("only the participant with kWh above the free kWh gets an invoice", docs, hasSize(1));
        BillingDocumentDTO invoice = document(docs, 3, INVOICE);
        assertAmounts(invoice, "6.00", null, null, null, null, "6.00");
        List<BillingDocumentItem> items = items(invoice.getId());
        assertThat(items, hasSize(1));
        assertItem(items.get(0), "60.00", "6.00", "0", "0", "6.00");
        assertThat(items.get(0).getText(),
                containsString("Menge (60,00 kWh) = Verbrauch (100,00 kWh) abzgl. freie kWh (40,00 kWh)"));
    }

    /**
     * S7 — discount, participant fee with discount, meter-point fees of a consumer and a producer meter,
     * all at 20 %: energy 200 kWh × 10 ct = 20.00 − 10 % = 18.00 (VAT 3.60); fee 12.50 − 20 % = 10.00 (VAT 2.00);
     * consumer meter fee 3.00 (tariff VAT, 0.60); producer meter fee 1.50 (meter VAT, 0.30).
     * Net 32.50, VAT 6.50, gross 39.00; the producer meter has 0 kWh, so no credit note.
     */
    @Test
    void s07_discountParticipantFeeAndMeterPointFees() {
        BillingRunFixture world = insert(world()
                .master(consumer(1, "C1").tariffVat("20").column("tariff_discount", new BigDecimal("10"))
                        .participantFee("12.50", "20").column("tariff_participant_fee_discount",
                                new BigDecimal("20"))
                        .meteringPointFee("3.00", "Zählpunkt C1", "0"), "200")
                .master(producer(1, "P1").participantFee("12.50", "20").column("tariff_participant_fee_discount",
                                new BigDecimal("20"))
                        .meteringPointFee("1.50", "Zählpunkt P1", "20"), "0"), false);

        DoBillingResults results = bill(world, false);

        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs, hasSize(1));
        BillingDocumentDTO invoice = document(docs, 1, INVOICE);
        assertAmounts(invoice, "32.50", "20", "6.50", null, null, "39.00");
        assertThat(invoice.getDocumentNumber(), is("TRECH202400042"));
        List<BillingDocumentItem> items = items(invoice.getId());
        assertThat(items, hasSize(4));
        assertItem(items.get(0), "200.00", "18.00", "20", "3.60", "21.60");
        assertThat(items.get(0).getDiscountPercent(), comparesEqualTo(new BigDecimal("10")));
        assertItem(items.get(1), "1", "10.00", "20", "2.00", "12.00");
        assertThat(items.get(1).getText(), is("Mitgliedsgebühr"));
        assertItem(items.get(2), "1", "3.00", "20", "0.60", "3.60");
        assertThat(items.get(2).getText(), is("Zählpunktgebühr: C1"));
        assertItem(items.get(3), "1", "1.50", "20", "0.30", "1.80");
        assertThat(items.get(3).getText(), is("Zählpunktgebühr: P1"));
    }

    /**
     * S7, fee without a VAT rate: VAT switched on for the participant fee but no rate stored. The run must
     * succeed and bill the fee with 0 % (the null-safe rate the amount is already computed with).
     * Known-errors #17 (F6): the raw {@code null} is stored and the VAT sum fails with an NPE, swallowed by F1.
     */
    @Test
    @Disabled("known-errors #17")
    void s07_participantFeeWithoutVatRateIsBilledWithZeroVat() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1")
                .column("tariff_participant_fee", new BigDecimal("10"))
                .column("tariff_participant_fee_use_vat", true)
                .column("tariff_participant_fee_vat_in_percent", null), "100"), false);

        DoBillingResults results = bill(world, true);

        assertThat(results.getAbstractText(), is(RESULT_PREVIEW_OK));
        BillingDocumentDTO invoice = document(documents(results.getBillingRunId()), 1, INVOICE);
        assertAmounts(invoice, "20.00", null, null, null, null, "20.00");
        List<BillingDocumentItem> items = items(invoice.getId());
        assertThat(items, hasSize(2));
        assertItem(items.get(1), "1", "10.00", "0", "0", "10.00");
    }

    /**
     * F15 (known-errors #26, suspicion): a run without a configuration uses {@code BillingConfigService.DEFAULT}.
     * Two runs of two communities must both print the default texts and must leave the shared default
     * untouched (not persisted, no tenant, no prefix).
     */
    @Test
    void f15_runsWithoutConfigurationUseTheUnchangedDefault() {
        BillingConfig defaults = BillingConfigService.DEFAULT;
        String firstTenant = tenant;
        BillingRunFixture first = world().master(consumer(1, "C1"), "100").insertInto(jdbc);

        DoBillingResults firstRun = bill(first, true);
        cleanUp();
        tenant = firstTenant + "B";
        BillingRunFixture second = world().master(consumer(1, "C1"), "200").insertInto(jdbc);
        DoBillingResults secondRun = bill(second, true);

        assertThat(firstRun.getAbstractText(), is(RESULT_PREVIEW_OK));
        assertThat(secondRun.getAbstractText(), is(RESULT_PREVIEW_OK));
        BillingDocumentDTO invoice = document(documents(secondRun.getBillingRunId()), 1, INVOICE);
        assertThat(invoice.getBeforeItemsText(), is("Wir erlauben uns folgende Rechnung zu stellen:"));
        assertThat(invoice.getTermsText(),
                is("Dieser Betrag wird in 2 Wochen von deinem Konto per Lastschrift eingezogen."));
        assertAmounts(invoice, "20.00", null, null, null, null, "20.00");
        assertThat(defaults.getId(), nullValue());
        assertThat(defaults.getTenantId(), nullValue());
        assertThat(defaults.getInvoiceNumberPrefix(), nullValue());
        assertThat(defaults.getHeaderImageFileDataId(), nullValue());
        assertThat(defaults.getBeforeItemsTextInvoice(), is("Wir erlauben uns folgende Rechnung zu stellen:"));
    }
}
