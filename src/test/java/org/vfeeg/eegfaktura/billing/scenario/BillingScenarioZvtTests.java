package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.service.BillingService.ZvtContractViolationException;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingMasterdataBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;

/**
 * S13 — the time-of-use tariff (ZVT, billing 1.0.8): one energy position per window share ("bucket"), and the
 * contract guard between the caller's payload and the tariff in the master data. The caller sends the kWh per
 * window ({@code BASE}, {@code T1}, {@code T2}) and the window definitions it used; billing refuses the run
 * when they do not fit the tariff (no silent base-price fallback). Amounts by hand in the comments.
 */
class BillingScenarioZvtTests extends BillingScenarioBase {

    /** Consumer with base 10 ct, window 1 "Tag" 06:00-08:00 at 22.5 ct, window 2 "Nacht" 20:00-06:00 at 5.5 ct. */
    private BillingMasterdataBuilder zvtConsumer() {
        return consumer(1, "C1").column("tariff_working_fee_per_consumedkwh", new BigDecimal("10"))
                .timeTariff().timeWindow(1, "Tag", "06:00", "08:00", "22.5").timeWindow(2, "Nacht", "20:00", "06:00", "5.5");
    }

    /** The payload of the consumer: the window shares and the windows as the caller knows them. */
    private AllocationBuilder payload(BillingMasterdataBuilder consumer) {
        return AllocationBuilder.allocation().participant(consumer.participantId()).meteringPoint(consumer.meteringPointId())
                .kwh("100").window("T1", "06:00", "08:00").window("T2", "20:00", "06:00");
    }

    /**
     * 50 kWh base × 10 ct = 5.00, 30 kWh "Tag" × 22.5 ct = 6.75, 20 kWh "Nacht" × 5.5 ct = 1.10: three energy
     * positions, each labelled with its window; net 12.85 (the fixture's consumer has no fee position).
     */
    @Test
    void s13_oneEnergyPositionPerWindowShare() {
        BillingMasterdataBuilder c1 = zvtConsumer();
        BillingRunFixture world = insert(world().master(c1, null), false);

        DoBillingResults results = bill(world, true, payload(c1).bucket("BASE", "50").bucket("T1", "30").bucket("T2", "20").build());

        assertThat(results.getAbstractText(), is(RESULT_PREVIEW_OK));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        BillingDocumentDTO invoice = document(docs, 1, INVOICE);
        assertThat(invoice.getNetAmountInEuro(), comparesEqualTo(new BigDecimal("12.85")));
        List<BillingDocumentItem> energy = energy(invoice);
        assertThat(energy, hasSize(3));
        assertEnergy(energy, "Tarif: Basis", "50", "5.00");
        assertEnergy(energy, "Tarif: Tag (06:00 - 08:00)", "30", "6.75");
        assertEnergy(energy, "Tarif: Nacht (20:00 - 06:00)", "20", "1.10");
    }

    /** A window without energy leaves no position; a window without a name is labelled by its times only. */
    @Test
    void s13_windowWithoutEnergyLeavesNoPositionAndANamelessWindowShowsItsTimes() {
        BillingMasterdataBuilder c1 = zvtConsumer().column("tariff_time1_name", " ");
        BillingRunFixture world = insert(world().master(c1, null), false);

        DoBillingResults results = bill(world, true, payload(c1).bucket("BASE", "50").bucket("T1", "30").bucket("T2", "0").build());

        BillingDocumentDTO invoice = document(documents(results.getBillingRunId()), 1, INVOICE);
        List<BillingDocumentItem> energy = energy(invoice);
        assertThat(energy, hasSize(2));
        assertEnergy(energy, "Tarif: (06:00 - 08:00)", "30", "6.75");
        assertThat(invoice.getNetAmountInEuro(), comparesEqualTo(new BigDecimal("11.75")));
    }

    /** Buckets for a plain tariff: the caller and the tariff do not fit, the run is refused. */
    @Test
    void s13_bucketsForAPlainTariffAreRefused() {
        BillingMasterdataBuilder c1 = consumer(1, "C1");
        BillingRunFixture world = insert(world().master(c1, null), false);

        assertRefused(world, payload(c1).bucket("BASE", "100").build(), "buckets im Payload, aber der Tarif ist nicht zeitbasiert");
    }

    @Test
    void s13_timeTariffWithoutBucketsIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer();
        BillingRunFixture world = insert(world().master(c1, null), false);

        assertRefused(world, payload(c1).build(), "zeitbasierter Tarif, aber keine Fenster-Teilsummen (buckets) im Payload");
    }

    @Test
    void s13_activeWindowWithoutAPriceIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer().column("tariff_time2_cent_per_kwh", null);
        BillingRunFixture world = insert(world().master(c1, null), false);

        assertRefused(world, payload(c1).bucket("BASE", "100").build(), "Zeitfenster 2 aktiv, aber ohne Preis");
    }

    @Test
    void s13_windowOfThePayloadMustMatchTheTariff() {
        BillingMasterdataBuilder c1 = zvtConsumer();
        BillingRunFixture world = insert(world().master(c1, null), false);
        AllocationBuilder stale = AllocationBuilder.allocation().participant(c1.participantId()).meteringPoint(c1.meteringPointId())
                .kwh("100").window("T1", "07:00", "08:00").window("T2", "20:00", "06:00").bucket("BASE", "100");

        assertRefused(world, stale.build(), "Zeitfenster T1 weicht von den aktuellen Tarif-Stammdaten ab (Payload 07:00-08:00, Tarif 06:00-08:00)");
    }

    @Test
    void s13_activeWindowMissingInThePayloadIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer();
        BillingRunFixture world = insert(world().master(c1, null), false);
        AllocationBuilder withoutT2 = AllocationBuilder.allocation().participant(c1.participantId()).meteringPoint(c1.meteringPointId())
                .kwh("100").window("T1", "06:00", "08:00").bucket("BASE", "100");

        assertRefused(world, withoutT2.build(), "Zeitfenster T2 ist im Tarif aktiv, fehlt aber im Payload");
    }

    @Test
    void s13_windowSentForAnInactiveWindowIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer().column("tariff_time2_active", false);
        BillingRunFixture world = insert(world().master(c1, null), false);

        assertRefused(world, payload(c1).bucket("BASE", "100").build(), "Zeitfenster T2 im Payload, ist im Tarif aber nicht aktiv");
    }

    @Test
    void s13_duplicateWindowKeyIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer();
        BillingRunFixture world = insert(world().master(c1, null), false);

        assertRefused(world, payload(c1).window("T1", "06:00", "08:00").bucket("BASE", "100").build(), "Zeitfenster-Key T1 mehrfach im Payload");
    }

    @Test
    void s13_duplicateBucketKeyIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer();
        BillingRunFixture world = insert(world().master(c1, null), false);

        assertRefused(world, payload(c1).bucket("BASE", "50").bucket("BASE", "50").build(), "Bucket-Key BASE mehrfach im Payload");
    }

    @Test
    void s13_bucketOfAnInactiveWindowIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer().column("tariff_time2_active", false);
        BillingRunFixture world = insert(world().master(c1, null), false);
        AllocationBuilder onlyT1 = AllocationBuilder.allocation().participant(c1.participantId()).meteringPoint(c1.meteringPointId())
                .kwh("100").window("T1", "06:00", "08:00").bucket("BASE", "50").bucket("T2", "50");

        assertRefused(world, onlyT1.build(), "Bucket T2, aber Zeitfenster 2 ist im Tarif nicht aktiv");
    }

    @Test
    void s13_unknownBucketKeyIsRefused() {
        BillingMasterdataBuilder c1 = zvtConsumer();
        BillingRunFixture world = insert(world().master(c1, null), false);

        assertRefused(world, payload(c1).bucket("BASE", "50").bucket("T3", "50").build(), "unbekannter Bucket-Key 'T3'");
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private DoBillingResults bill(BillingRunFixture world, boolean preview, Allocation... allocations) {
        DoBillingParams params = world.params(PERIOD_TYPE, PERIOD, preview);
        params.setClearingDocumentDate(REFERENCE_DATE);
        params.setAllocations(allocations);
        return billingService.doBilling(params);
    }

    private void assertRefused(BillingRunFixture world, Allocation allocation, String reason) {
        ZvtContractViolationException refused = assertThrows(ZvtContractViolationException.class, () -> bill(world, true, allocation));
        assertThat(refused.getMessage(), containsString("Zaehlpunkt " + allocation.getMeteringPoint() + ": " + reason));
        assertThat(count("billing_document where tenant_id = ?"), is(0));
    }

    /** The energy positions of an invoice (the others carry no window label); the order is not part of the contract. */
    private List<BillingDocumentItem> energy(BillingDocumentDTO invoice) {
        return items(invoice.getId()).stream().filter(i -> i.getText().contains("\nTarif: ")).toList();
    }

    private static void assertEnergy(List<BillingDocumentItem> energy, String label, String kwh, String net) {
        List<BillingDocumentItem> labelled = energy.stream().filter(i -> i.getText().endsWith("\n" + label)).toList();
        assertThat("one position " + label, labelled, hasSize(1));
        assertThat("kWh of " + label, labelled.get(0).getAmount(), comparesEqualTo(new BigDecimal(kwh)));
        assertThat("net of " + label, labelled.get(0).getNetValue(), comparesEqualTo(new BigDecimal(net)));
    }
}
