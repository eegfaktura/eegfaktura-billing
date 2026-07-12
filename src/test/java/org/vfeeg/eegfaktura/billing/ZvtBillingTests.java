package org.vfeeg.eegfaktura.billing;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.AllocationBucket;
import org.vfeeg.eegfaktura.billing.model.AllocationTimeWindow;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Zeitvariabler Tarif (konzept-zeitvariable-tarife.md): 1-3 Positionen je
 * Zaehlpunkt aus den Fenster-Teilsummen, fail-loud Kontrakt-Guards,
 * Einfach-Tarif-Regression im gemischten Lauf.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ZvtBillingTests {

    static final String TENANT = "TE100100";
    static final String ZVT_PARTICIPANT = "11111111-2222-3333-4444-555555555555";
    static final String ZVT_ZP = "C0000000000000000000005555";
    static final String SIMPLE_PARTICIPANT = "8126ab63-3f5d-42a4-b6f5-8df17aa68158";
    static final String SIMPLE_ZP = "C0000000000000000000001234";

    @Container
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withUsername("sa")
            .withPassword("sa")
            .withReuse(true);

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    @Autowired
    BillingService billingService;

    @Autowired
    BillingDocumentItemRepository billingDocumentItemRepository;

    private DoBillingParams params(String periodIdentifier, Allocation... allocations) {
        DoBillingParams params = new DoBillingParams();
        params.setTenantId(TENANT);
        params.setClearingPeriodType("QUARTERLY");
        params.setClearingPeriodIdentifier(periodIdentifier);
        params.setPreview(true);
        params.setAllocations(allocations);
        return params;
    }

    private static Allocation zvtAllocation(BigDecimal base, BigDecimal t1, BigDecimal t2) {
        Allocation allocation = new Allocation();
        allocation.setParticipantId(ZVT_PARTICIPANT);
        allocation.setMeteringPoint(ZVT_ZP);
        allocation.setAllocationKWh(base.add(t1).add(t2));
        allocation.setBuckets(new AllocationBucket[]{
                bucket("BASE", base), bucket("T1", t1), bucket("T2", t2)});
        allocation.setTimeWindows(new AllocationTimeWindow[]{
                window("T1", "06:00", "08:00"), window("T2", "20:00", "06:00")});
        return allocation;
    }

    private static AllocationBucket bucket(String key, BigDecimal kWh) {
        AllocationBucket bucket = new AllocationBucket();
        bucket.setKey(key);
        bucket.setKWh(kWh);
        return bucket;
    }

    private static AllocationTimeWindow window(String key, String from, String to) {
        AllocationTimeWindow tw = new AllocationTimeWindow();
        tw.setKey(key);
        tw.setFrom(from);
        tw.setTo(to);
        return tw;
    }

    @Test
    @Sql("/billing_master_data.sql")
    void zvtRunCreatesOnePositionPerBucket() {
        DoBillingResults results = billingService.doBilling(params("Abr_YQ-2024-1",
                zvtAllocation(new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("30"))));

        List<BillingDocumentItem> items = billingDocumentItemRepository
                .findByBillingRunId(results.getBillingRunId()).stream()
                .filter(i -> ZVT_ZP.equals(i.getMeteringPointId())).toList();
        assertThat(items, hasSize(3));

        BillingDocumentItem base = itemWithTextContaining(items, "Tarif: Basis");
        assertThat(base.getAmount(), comparesEqualTo(new BigDecimal("100")));
        assertThat(base.getPricePerUnit(), comparesEqualTo(new BigDecimal("12.83")));
        // 100 kWh x 12.83 ct = 12.83 EUR, abzgl. 10% Rabatt (1.28) = 11.55
        assertThat(base.getNetValue(), comparesEqualTo(new BigDecimal("11.55")));

        BillingDocumentItem t1 = itemWithTextContaining(items, "Tarif: Tag (06:00 - 08:00)");
        assertThat(t1.getAmount(), comparesEqualTo(new BigDecimal("50")));
        assertThat(t1.getPricePerUnit(), comparesEqualTo(new BigDecimal("22.5")));
        // 50 x 22.5 ct = 11.25, abzgl. 10% (1.13) = 10.12
        assertThat(t1.getNetValue(), comparesEqualTo(new BigDecimal("10.12")));

        BillingDocumentItem t2 = itemWithTextContaining(items, "Tarif: Nacht (20:00 - 06:00)");
        assertThat(t2.getAmount(), comparesEqualTo(new BigDecimal("30")));
        assertThat(t2.getPricePerUnit(), comparesEqualTo(new BigDecimal("5.5")));
        // 30 x 5.5 ct = 1.65, abzgl. 10% (0.17) = 1.48
        assertThat(t2.getNetValue(), comparesEqualTo(new BigDecimal("1.48")));

        // ParticipantAmounts: genau EIN MeteringPoint-Eintrag je ZP (aggregiert)
        assertThat(results.getParticipantAmounts(), hasSize(1));
        assertThat(results.getParticipantAmounts().get(0).getMeteringPoints(), hasSize(1));
        assertThat(results.getParticipantAmounts().get(0).getMeteringPoints().get(0).getAmount(),
                comparesEqualTo(new BigDecimal("23.15"))); // 11.55 + 10.12 + 1.48
    }

    @Test
    @Sql("/billing_master_data.sql")
    void zvtLeereBucketsWerdenAlsNullpositionGezeigt() {
        DoBillingResults results = billingService.doBilling(params("Abr_YQ-2024-2",
                zvtAllocation(new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("30"))));

        List<BillingDocumentItem> items = billingDocumentItemRepository
                .findByBillingRunId(results.getBillingRunId()).stream()
                .filter(i -> ZVT_ZP.equals(i.getMeteringPointId())).toList();
        // ZVT: jede Tarifoption wird gezeigt, AUCH bei 0 kWh, damit keine zu fehlen scheint.
        assertThat(items, hasSize(3));
        BillingDocumentItem t1 = itemWithTextContaining(items, "Tarif: Tag (06:00 - 08:00)");
        assertThat(t1.getAmount(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(t1.getNetValue(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    @Sql("/billing_master_data.sql")
    void zvtOhneBucketsBrichtAb() {
        Allocation allocation = zvtAllocation(new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("30"));
        allocation.setBuckets(null);
        BillingService.ZvtContractViolationException ex =
                assertThrows(BillingService.ZvtContractViolationException.class,
                        () -> billingService.doBilling(params("Abr_YQ-2024-3", allocation)));
        assertThat(ex.getMessage(), containsString("keine Fenster-Teilsummen"));
    }

    @Test
    @Sql("/billing_master_data.sql")
    void zvtMitAbweichendemFensterBrichtAb() {
        Allocation allocation = zvtAllocation(new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("30"));
        allocation.setTimeWindows(new AllocationTimeWindow[]{
                window("T1", "06:00", "09:00"), window("T2", "20:00", "06:00")});
        BillingService.ZvtContractViolationException ex =
                assertThrows(BillingService.ZvtContractViolationException.class,
                        () -> billingService.doBilling(params("Abr_YQ-2024-4", allocation)));
        assertThat(ex.getMessage(), containsString("weicht von den aktuellen Tarif-Stammdaten ab"));
    }

    @Test
    @Sql("/billing_master_data.sql")
    void bucketsBeiEinfachTarifBrechenAb() {
        Allocation allocation = new Allocation();
        allocation.setParticipantId(SIMPLE_PARTICIPANT);
        allocation.setMeteringPoint(SIMPLE_ZP);
        allocation.setAllocationKWh(new BigDecimal("100"));
        allocation.setBuckets(new AllocationBucket[]{bucket("BASE", new BigDecimal("100"))});
        BillingService.ZvtContractViolationException ex =
                assertThrows(BillingService.ZvtContractViolationException.class,
                        () -> billingService.doBilling(params("Abr_YQ-2024-5", allocation)));
        assertThat(ex.getMessage(), containsString("nicht zeitbasiert"));
    }

    @Test
    @Sql("/billing_master_data.sql")
    void gemischterLaufRechnetEinfachTarifWieBisher() {
        Allocation simple = new Allocation();
        simple.setParticipantId(SIMPLE_PARTICIPANT);
        simple.setMeteringPoint(SIMPLE_ZP);
        simple.setAllocationKWh(new BigDecimal("100"));

        DoBillingResults results = billingService.doBilling(params("Abr_YQ-2024-6",
                simple, zvtAllocation(new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("3"))));

        List<BillingDocumentItem> simpleItems = billingDocumentItemRepository
                .findByBillingRunId(results.getBillingRunId()).stream()
                .filter(i -> SIMPLE_ZP.equals(i.getMeteringPointId())).toList();
        // Einfach-Tarif: weiterhin genau EINE Energie-Position, Betrag wie bisher
        assertThat(simpleItems, hasSize(1));
        assertThat(simpleItems.get(0).getNetValue(), comparesEqualTo(new BigDecimal("12.83")));
        assertThat(simpleItems.get(0).getPricePerUnit(), comparesEqualTo(new BigDecimal("12.83")));
    }

    private static BillingDocumentItem itemWithTextContaining(List<BillingDocumentItem> items, String needle) {
        return items.stream().filter(i -> i.getText() != null && i.getText().contains(needle))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Kein Item mit Text '" + needle + "' gefunden. Vorhanden: "
                                + items.stream().map(BillingDocumentItem::getText).toList()));
    }
}
