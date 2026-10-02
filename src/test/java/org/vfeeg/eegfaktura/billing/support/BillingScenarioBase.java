package org.vfeeg.eegfaktura.billing.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.service.BillingRunService;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Base of the billing scenarios (M3) and of the concurrency/mail tests (M5).
 *
 * <p>One Spring context on the JVM-wide {@link PostgresContainerHolder}. The tests are <b>not</b>
 * {@code @Transactional}: {@code doBilling} commits or rolls back on its own, so what survives a failed run
 * is visible (known-errors #12). Every test gets its own community id ({@link #tenant}); {@link #cleanUp()}
 * deletes all rows of that community afterwards, including the committed rows of
 * {@code base.billing_masterdata}. Dates come from {@link #REFERENCE_DATE}, never from {@code LocalDate.now()}.
 */
@SpringBootTest
public abstract class BillingScenarioBase {

    public static final LocalDate REFERENCE_DATE = LocalDate.of(2024, 6, 28);
    public static final String PERIOD_TYPE = "QUARTERLY";
    public static final String PERIOD = "Abr_YQ-2024-2";
    /** Result text of a successful final run (the blank before the colon is the production text). */
    public static final String RESULT_FINAL_OK = "Abrechnung : erfolgreich abgeschlossen.";
    public static final String RESULT_PREVIEW_OK = "Abrechnung (Vorschau): erfolgreich abgeschlossen.";
    public static final String RESULT_FAILED = "Abrechnung fehlgeschlagen: ";

    private static final AtomicInteger TENANTS = new AtomicInteger();

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected BillingService billingService;
    @Autowired
    protected BillingConfigService billingConfigService;
    @Autowired
    protected BillingRunService billingRunService;
    @Autowired
    protected BillingDocumentService billingDocumentService;
    @Autowired
    protected BillingDocumentItemRepository itemRepository;
    @Autowired
    protected BillingDocumentFileRepository documentFileRepository;
    @Autowired
    protected FileDataRepository fileDataRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** The community of the current test, unique per test in this JVM. */
    protected String tenant;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresContainerHolder.register(registry);
    }

    @BeforeEach
    protected void newTenant() {
        tenant = "SC%06d".formatted(TENANTS.incrementAndGet());
        try {
            jdbc.execute(new ClassPathResource("base_masterdata_ddl.sql").getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Deletes every row of {@link #tenant}, children first (the foreign keys have no cascade). */
    @AfterEach
    protected void cleanUp() {
        String documentsOfTenant = "(select id from billing_document where tenant_id = ?)";
        jdbc.update("delete from billing_document_item where billing_document_id in " + documentsOfTenant, tenant);
        jdbc.update("delete from billing_document_file where tenant_id = ? or billing_document_id in "
                + documentsOfTenant, tenant, tenant);
        jdbc.update("delete from billing_document where tenant_id = ?", tenant);
        jdbc.update("delete from billing_run where tenant_id = ?", tenant);
        jdbc.update("delete from billing_document_number where tenant_id = ?", tenant);
        jdbc.update("delete from billing_config where tenant_id = ?", tenant);
        jdbc.update("delete from file_data where tenant_id = ?", tenant);
        jdbc.update("delete from base.billing_masterdata where tenant_id = ?", tenant);
    }

    // ---- data ------------------------------------------------------------------------------------

    /** Participant id {@code n} as a valid UUID (the service parses it). */
    public static String participant(int n) {
        return "00000000-0000-4000-8000-%012d".formatted(n);
    }

    /** Consumer at 10 ct/kWh without VAT and without participant fee; scenarios set what they test. */
    public static BillingMasterdataBuilder consumer(int participant, String meteringPoint) {
        return BillingMasterdataBuilder.consumer(participant(participant), meteringPoint)
                .name(null, "Vorname" + participant, "Nachname" + participant, null)
                .workingFee("10").participantFee("0", null);
    }

    /** Producer at 8 ct/kWh credit without VAT and without participant fee. */
    public static BillingMasterdataBuilder producer(int participant, String meteringPoint) {
        return BillingMasterdataBuilder.producer(participant(participant), meteringPoint)
                .name(null, "Vorname" + participant, "Nachname" + participant, null)
                .creditAmount("8").participantFee("0", null);
    }

    protected BillingRunFixture world() {
        return BillingRunFixture.forTenant(tenant);
    }

    /** Inserts the master rows and creates the configuration (prefixes TRECH/TGUT, starts 42/73). */
    protected BillingRunFixture insert(BillingRunFixture world, boolean creditNotesForAllProducers) {
        world.insertInto(jdbc);
        world.createConfig(billingConfigService, fileDataRepository, creditNotesForAllProducers);
        return world;
    }

    // ---- runs ------------------------------------------------------------------------------------

    protected DoBillingResults bill(BillingRunFixture world, boolean preview) {
        return bill(world, preview, REFERENCE_DATE);
    }

    protected DoBillingResults bill(BillingRunFixture world, boolean preview, LocalDate documentDate) {
        DoBillingParams params = world.params(PERIOD_TYPE, PERIOD, preview);
        params.setClearingDocumentDate(documentDate);
        return billingService.doBilling(params);
    }

    // ---- reading ---------------------------------------------------------------------------------

    /** Documents of a run, sorted by recipient and type (no reliance on database order, T4). */
    protected List<BillingDocumentDTO> documents(UUID billingRunId) {
        return billingDocumentService.findByBillingRunId(billingRunId).stream()
                .sorted(Comparator.comparing(BillingDocumentDTO::getRecipientName)
                        .thenComparing(BillingDocumentDTO::getBillingDocumentType))
                .toList();
    }

    protected static BillingDocumentDTO document(List<BillingDocumentDTO> documents, int participant,
                                                 BillingDocumentType type) {
        return documents.stream()
                .filter(d -> d.getParticipantId().equals(participant(participant)) && d.getBillingDocumentType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " for participant " + participant));
    }

    /** Items of a document, sorted by text. */
    protected List<BillingDocumentItem> items(UUID documentId) {
        return itemRepository.findByBillingDocument_Id(documentId).stream()
                .sorted(Comparator.comparing(BillingDocumentItem::getText))
                .toList();
    }

    /** {@code select count(*) from <fromWhere>} with the tenant as the only parameter. */
    protected int count(String fromWhere) {
        Integer n = jdbc.queryForObject("select count(*) from " + fromWhere, Integer.class, tenant);
        return n == null ? 0 : n;
    }

    /** Consumed document numbers of the tenant, sorted. */
    protected List<String> documentNumbers() {
        return jdbc.queryForList("select document_number from billing_document_number where tenant_id = ?"
                + " order by document_number", String.class, tenant);
    }

    /** The stored PDF of a document (large objects can only be read inside a transaction). */
    protected byte[] pdf(UUID documentId) {
        return inTransaction(() -> {
            var files = documentFileRepository.findByBillingDocumentId(documentId);
            assertThat("one PDF per document", files.size(), is(1));
            return fileDataRepository.findById(files.get(0).getFileDataId()).orElseThrow().getData();
        });
    }

    protected <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    // ---- assertions ------------------------------------------------------------------------------

    /** Net, VAT sums and gross of a document; {@code null} for a VAT rate means "not set". */
    protected static void assertAmounts(BillingDocumentDTO doc, String net, String vat1Percent, String vat1Sum,
                                        String vat2Percent, String vat2Sum, String gross) {
        assertThat("net", doc.getNetAmountInEuro(), comparesEqualTo(new BigDecimal(net)));
        assertDecimal("vat1Percent", doc.getVat1Percent(), vat1Percent);
        assertDecimal("vat1Sum", doc.getVat1SumInEuro(), vat1Sum);
        assertDecimal("vat2Percent", doc.getVat2Percent(), vat2Percent);
        assertDecimal("vat2Sum", doc.getVat2SumInEuro(), vat2Sum);
        assertThat("gross", doc.getGrossAmountInEuro(), comparesEqualTo(new BigDecimal(gross)));
    }

    /** Amount (kWh or 1), net, VAT and gross of an item. */
    protected static void assertItem(BillingDocumentItem item, String amount, String net, String vatPercent,
                                     String vat, String gross) {
        assertThat("amount of " + item.getText(), item.getAmount(), comparesEqualTo(new BigDecimal(amount)));
        assertThat("net of " + item.getText(), item.getNetValue(), comparesEqualTo(new BigDecimal(net)));
        assertDecimal("vatPercent of " + item.getText(), item.getVatPercent(), vatPercent);
        assertThat("vat of " + item.getText(), item.getVatValueInEuro(), comparesEqualTo(new BigDecimal(vat)));
        assertThat("gross of " + item.getText(), item.getGrossValue(), comparesEqualTo(new BigDecimal(gross)));
    }

    private static void assertDecimal(String what, BigDecimal actual, String expected) {
        if (expected == null) {
            assertThat(what, actual, nullValue());
        } else {
            assertThat(what, actual, comparesEqualTo(new BigDecimal(expected)));
        }
    }
}
