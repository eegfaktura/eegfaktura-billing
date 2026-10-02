package org.vfeeg.eegfaktura.billing;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import junit.framework.AssertionFailedError;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.MimeTypeUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.vfeeg.eegfaktura.billing.domain.*;
import org.vfeeg.eegfaktura.billing.model.*;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentNumberGenerator;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.service.*;
import org.vfeeg.eegfaktura.billing.support.DocumentReaders;
import org.vfeeg.eegfaktura.billing.util.BigDecimalTools;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@Testcontainers
@Transactional
class BillingIntegrationTests {

    public final static String[][] TEST_ALLOCATIONS = new String[][] {
        {"8126ab63-3f5d-42a4-b6f5-8df17aa68158", "C0000000000000000000001234", "120.3489"},
        {"8126ab63-3f5d-42a4-b6f5-8df17aa68158", "C0000000000000000000002234", "777.5976"},
        {"039e8d60-b6ba-459c-b5a1-0c31aa53a490", "P0000000000000000000002222", "2233.2209"},
        {"bf6c5e6c-a7f2-4499-b2bb-02bb6587b951", "P0000000000000000000003333", "3355.3323"},
        {"bf6c5e6c-a7f2-4499-b2bb-02bb6587b951", "P0000000000000000000004444", "4477.4499"}
    };

    @Autowired
    BillingConfigService billingConfigService;

    @Autowired
    BillingRunService billingRunService;

    @Autowired
    BillingService billingService;

    @Autowired
    BillingMasterdataRepository billingMasterdataRepository;

    @Autowired
    TestAppProperties testAppProperties;

    @Autowired
    FileDataRepository fileDataRepository;

    @Autowired
    BillingDocumentService billingDocumentService;

    @Autowired
    BillingDocumentNumberGenerator billingDocumentNumberGenerator;

    @Autowired
    BillingDocumentItemRepository billingDocumentItemRepository;

    @Autowired
    BillingDocumentXlsxService billingDocumentXlsxService;

    @Container
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withUsername("sa")
            .withPassword("sa")
            .withReuse(true);

    UUID createBillingConfig(boolean createCreditNotesForAllProducers) {

        UUID headerImageFileDataId;
        try {
            final byte[] defaultLogoByteArray = new ClassPathResource("eeg-faktura-logo.png").getContentAsByteArray();
            FileData fileData = new FileData();
            fileData.setName("eeg-faktura-logo.png");
            fileData.setMimeType(MimeTypeUtils.IMAGE_PNG.toString());
            fileData.setTenantId("TE100100");
            fileData.setData(defaultLogoByteArray);
            headerImageFileDataId = fileDataRepository.save(fileData).getId();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        BillingConfigDTO billingConfigDTO = new BillingConfigDTO();
        billingConfigDTO.setTenantId("TE100100");
        billingConfigDTO.setHeaderImageFileDataId(headerImageFileDataId);
        //private UUID footerImageFileDataId; // NOT USED!!
        billingConfigDTO.setCreateCreditNotesForAllProducers(createCreditNotesForAllProducers);
        billingConfigDTO.setBeforeItemsTextInvoice("Text vor den Positionen (Rechnung) ## 2. Zeile dazu");
        billingConfigDTO.setBeforeItemsTextCreditNote("Text vor den Positionen (Gutschrift) ## 2. Zeile dazu");
        billingConfigDTO.setBeforeItemsTextInfo("Text vor den Positionen (Rechnungsimformation) ## 2. Zeile dazu");
        billingConfigDTO.setAfterItemsTextInvoice("Text NACH den Positionen (Rechnung) ## 2. Zeile dazu");
        billingConfigDTO.setAfterItemsTextCreditNote("Text NACH den Positionen (Gutschrift) ## 2. Zeile dazu");
        billingConfigDTO.setAfterItemsTextInfo("Text NACH den Positionen (Rechnungsimformation) ## 2. Zeile dazu");
        billingConfigDTO.setTermsTextInvoice("Textbereich für Bedingungen (Rechnung) ## 2. Zeile dazu");
        billingConfigDTO.setTermsTextCreditNote("Textbereich für Bedingungen (Gutschrift) ## 2. Zeile dazu");
        billingConfigDTO.setTermsTextInfo("Textbereich für Bedingungen (Rechnungsimformation) ## 2. Zeile dazu");
        billingConfigDTO.setFooterText("Text für Fußzeile ## 2. Zeile mit etwas längerem Text ## 3. Zeile mit weiterem Text");
        billingConfigDTO.setDocumentNumberSequenceLength(5);
        //billingConfigDTO.setCustomTemplateFileDataId; // NOT USED!!
        billingConfigDTO.setInvoiceNumberPrefix("TRECH");
        billingConfigDTO.setInvoiceNumberStart(42L);
        billingConfigDTO.setCreditNoteNumberPrefix("TGUT");
        billingConfigDTO.setCreditNoteNumberStart(73L);

        return billingConfigService.create(billingConfigDTO);
    }

    @Test
    void testBillingConfig() {
        BillingConfigDTO billingConfigDTO = new BillingConfigDTO();

        billingConfigDTO.setTenantId("XYZ");
        //private UUID headerImageFileDataId;
        //private UUID footerImageFileDataId; // NOT USED!!
        billingConfigDTO.setBeforeItemsTextInvoice("setBeforeItemsTextInvoice");
        billingConfigDTO.setBeforeItemsTextCreditNote("setBeforeItemsTextCreditNote");
        billingConfigDTO.setBeforeItemsTextInfo("setBeforeItemsTextInfo)");
        billingConfigDTO.setAfterItemsTextInvoice("setAfterItemsTextInvoice");
        billingConfigDTO.setAfterItemsTextCreditNote("setAfterItemsTextCreditNote");
        billingConfigDTO.setAfterItemsTextInfo("setAfterItemsTextInfo");
        billingConfigDTO.setTermsTextInvoice("setTermsTextInvoice");
        billingConfigDTO.setTermsTextCreditNote("setTermsTextCreditNote");
        billingConfigDTO.setTermsTextInfo("setTermsTextInfo");
        billingConfigDTO.setFooterText("setFooterText");
        billingConfigDTO.setDocumentNumberSequenceLength(9);
        //billingConfigDTO.setCustomTemplateFileDataId; // NOT USED!!
        billingConfigDTO.setInvoiceNumberPrefix("INVPREFIX");
        billingConfigDTO.setInvoiceNumberStart(1L);
        billingConfigDTO.setCreditNoteNumberPrefix("CREDPREFIX");
        billingConfigDTO.setCreditNoteNumberStart(100L);

        UUID billingConfigId = billingConfigService.create(billingConfigDTO);
        BillingConfigDTO loadedBillingConfigDTO = billingConfigService.get(billingConfigId);
        assertThat(loadedBillingConfigDTO, samePropertyValuesAs(billingConfigDTO, "id"));
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingMasterdata() {

        List<BillingMasterdata> billingMasterdataList = billingMasterdataRepository.findByTenantId("TE100100");
        assertThat(billingMasterdataList, not(empty()));
        BillingMasterdata billingMasterdata = billingMasterdataList.stream().filter(
                e -> e.getMeteringPointId().equals("C0000000000000000000001234")).findFirst().orElse(null);
        assertThat(billingMasterdata, notNullValue());
        assertThat(billingMasterdata, allOf(
                hasProperty("participantTitleBefore", is("Mag.")),
                hasProperty("participantFirstname", is("Felix")),
                hasProperty("participantLastname", is("Glück")),
                hasProperty("participantTitleAfter", is("Msc")),
                hasProperty("participantVatId", is("UST12345")),
                hasProperty("participantTaxId", is("STR12345")),
                hasProperty("participantCompanyRegisterNumber", is("FN12312A")),
                hasProperty("participantEmail", is("harald.lacherstorfer@gmail.com")),
                hasProperty("participantStreet", is("Glücksweg 13")),
                hasProperty("equipmentNumber", is("Anlagenr 1234")),
                hasProperty("meteringEquipmentName", is("Anlage Foo-Bar")),
                hasProperty("meteringPointType", is(MeteringPointType.CONSUMER)),
                hasProperty("tariffWorkingFeePerConsumedkwh", comparesEqualTo(new BigDecimal("12.83"))),
                hasProperty("tariffParticipantFee", comparesEqualTo(BigDecimal.TEN)),
                hasProperty("eecName", is("Energiegemeinschaft Holy Grail")),
                hasProperty("eecId", is("TE100100")),
                hasProperty("tariffCreditAmountPerProducedkwh", is(BigDecimal.valueOf(19))),
                hasProperty("tariffId", is("75d44a4f-35ef-11ef-9d95-b657056770ae")),
                hasProperty("tariffVersion", is(13))
        ));

    }

    /** {@code fixedDate} null: the run's own date, which lies between {@code runDay} and today (T7). */
    void assertBillingRunValid(UUID billingRunId, boolean isPreview, LocalDate fixedDate, LocalDate runDay,
                               boolean createCreditNotesForAllProducers) {

        List<BillingDocumentDTO> billingDocumentDTOList = billingDocumentService.findByBillingRunId(
                billingRunId);
        assertThat(billingDocumentDTOList, hasSize(5));

        for (BillingDocumentDTO billingDocumentDTO : billingDocumentDTOList) {

            if (fixedDate != null) {
                assertThat(billingDocumentDTO.getDocumentDate(), is(fixedDate));
            } else {
                assertThat(billingDocumentDTO.getDocumentDate(), allOf(greaterThanOrEqualTo(runDay),
                        lessThanOrEqualTo(LocalDate.now())));
            }

            // sorted by text: no reliance on the database order (T4)
            List<BillingDocumentItem> billingDocumentItemList = billingDocumentItemRepository
                    .findByBillingDocument_Id(billingDocumentDTO.getId()).stream()
                    .sorted(Comparator.comparing(BillingDocumentItem::getText)).toList();

            if (billingDocumentDTO.getRecipientName().equals("Sonne GmbH")
                    && billingDocumentDTO.getBillingDocumentType()==BillingDocumentType.INVOICE) {
                //Prüfe Rechnungsvorschau für Teilnehmer "Sonne GmbH"
                assertThat(assertIsInvoiceSonneGmbH(billingDocumentDTO, billingDocumentItemList, isPreview)
                        , is(true));
            } else if (billingDocumentDTO.getRecipientName().equals("Sonne GmbH")
                    && billingDocumentDTO.getBillingDocumentType()==BillingDocumentType.INFO){
                //Prüfe Info für Teilnehmer "Sonne GmbH"
                assertThat(assertIsInfoOrCreditNoteRcSonneGmbH(billingDocumentDTO, billingDocumentItemList,
                                createCreditNotesForAllProducers)
                        , is(true));
            } else if (billingDocumentDTO.getRecipientName().equals("Sonne GmbH")
                    && billingDocumentDTO.getBillingDocumentType()==BillingDocumentType.CREDIT_NOTE_RC){
                //Prüfe Gutschrift (RC) für Teilnehmer "Sonne GmbH"
                assertThat(assertIsInfoOrCreditNoteRcSonneGmbH(billingDocumentDTO, billingDocumentItemList,
                                createCreditNotesForAllProducers)
                        , is(true));
            } else if (billingDocumentDTO.getRecipientName().equals("Mag. Felix Glück Msc")
                    && billingDocumentDTO.getBillingDocumentType()==BillingDocumentType.INVOICE){
                //Prüfe Rechnungs Vorschau für Teilnehmer "Felix Glück"
                assertThat(assertIsInvoiceGlueck(billingDocumentDTO, billingDocumentItemList, isPreview)
                        , is(true));
            } else if (billingDocumentDTO.getRecipientName().equals("Fridolin Fröhlich MBA")
                    && billingDocumentDTO.getBillingDocumentType()==BillingDocumentType.INVOICE){
                //Prüfe Rechnungs Vorschau für Teilnehmer "Fridolin Fröhlich"
                assertThat(assertIsInvoiceFroehlich(billingDocumentDTO, billingDocumentItemList, isPreview)
                        , is(true));
            } else if (billingDocumentDTO.getRecipientName().equals("Fridolin Fröhlich MBA")
                    && billingDocumentDTO.getBillingDocumentType()==BillingDocumentType.CREDIT_NOTE){
                //Prüfe Gutschrift-Vorschau für Teilnehmer "Fridolin Fröhlich"
                assertThat(assertIsCreditNoteFroehlich(billingDocumentDTO, billingDocumentItemList, isPreview)
                        , is(true));
            } else {
                throw new RuntimeException("Expected document to test not found. RecipientName is " +
                        billingDocumentDTO.getRecipientName() +
                        ", billingDocumentType is "+billingDocumentDTO.getBillingDocumentType());
            }
        }

    }

    void assertIssuerDataValid(BillingDocumentDTO billingDocumentDTO) {
        assertThat(billingDocumentDTO.getTenantId(), is("TE100100"));
        assertThat(billingDocumentDTO.getIssuerName(), is("Energiegemeinschaft Holy Grail"));
        assertThat(billingDocumentDTO.getIssuerTaxId(), is("STR4321"));
        assertThat(billingDocumentDTO.getIssuerVatId(), is("UST4321"));
        assertThat(billingDocumentDTO.getIssuerCompanyRegisterNumber(), is("FN4321A"));
        assertThat(billingDocumentDTO.getIssuerBankName(), is("Sparkasse OÖ"));
        assertThat(billingDocumentDTO.getIssuerBankIBAN(), is("AT01-4321-4321-4321"));
        assertThat(billingDocumentDTO.getIssuerBankOwner(), is("Energiegemeinschaft Holy Grail"));
        assertThat(billingDocumentDTO.getIssuerAddressLine1(), is("Feldweg 12"));
        assertThat(billingDocumentDTO.getIssuerAddressLine2(), is("1234 Fuxholzen"));
        assertThat(billingDocumentDTO.getIssuerPhone(), is("+43 555 123456"));
        assertThat(billingDocumentDTO.getIssuerMail(), is("eeg-holy-grail@gmx.at"));
    }

    void assertSonneGmbHDataValid(BillingDocumentDTO billingDocumentDTO) {
        assertThat(billingDocumentDTO.getRecipientName(), is("Sonne GmbH"));
        assertThat(billingDocumentDTO.getRecipientLastname(), is("Sonne GmbH"));
        assertThat(billingDocumentDTO.getRecipientAddressLine1(), is("Sonnenweg 42"));
        assertThat(billingDocumentDTO.getRecipientAddressLine2(), is("1234 Fuxholzen"));
        assertThat(billingDocumentDTO.getRecipientEmail(), is("harald.lacherstorfer@gmail.com"));
        assertThat(billingDocumentDTO.getRecipientSepaMandateReference(), is("REF3333"));
        assertThat(billingDocumentDTO.getRecipientSepaMandateIssueDate(),
                is( LocalDate.of(2023,4, 1)));
        assertThat(billingDocumentDTO.getRecipientBankName(), is("Postsparkasse"));
        assertThat(billingDocumentDTO.getRecipientBankOwner(), is("Sonne GmbH"));
        assertThat(billingDocumentDTO.getRecipientBankIban(), is("AT01-3333-3333-333"));
    }

    void assertFelixGlueckDataValid(BillingDocumentDTO billingDocumentDTO) {
        assertThat(billingDocumentDTO.getRecipientName(), is("Mag. Felix Glück Msc"));
        assertThat(billingDocumentDTO.getRecipientFirstname(), is("Felix"));
        assertThat(billingDocumentDTO.getRecipientLastname(), is("Glück"));
        assertThat(billingDocumentDTO.getRecipientAddressLine1(), is("Glücksweg 13"));
        assertThat(billingDocumentDTO.getRecipientAddressLine2(), is("1234 Fuxholzen"));
        assertThat(billingDocumentDTO.getRecipientEmail(), is("harald.lacherstorfer@gmail.com"));
        assertThat(billingDocumentDTO.getRecipientSepaMandateReference(), is("REF1234"));
        assertThat(billingDocumentDTO.getRecipientSepaMandateIssueDate(),
                is( LocalDate.of(2022,1, 1)));
        assertThat(billingDocumentDTO.getRecipientBankName(), is("Raiffeisen Landesbank"));
        assertThat(billingDocumentDTO.getRecipientBankOwner(), is("Felix Glück"));
        assertThat(billingDocumentDTO.getRecipientBankIban(), is("AT01-1234-1234-1234"));
    }

    void assertFridolinFroehlichDataValid(BillingDocumentDTO billingDocumentDTO) {
        assertThat(billingDocumentDTO.getRecipientName(), is("Fridolin Fröhlich MBA"));
        assertThat(billingDocumentDTO.getRecipientFirstname(), is("Fridolin"));
        assertThat(billingDocumentDTO.getRecipientLastname(), is("Fröhlich"));
        assertThat(billingDocumentDTO.getRecipientAddressLine1(), is("Fröhlichweg 15"));
        assertThat(billingDocumentDTO.getRecipientAddressLine2(), is("1234 Fuxholzen"));
        assertThat(billingDocumentDTO.getRecipientEmail(), is("harald.lacherstorfer@gmail.com"));
        assertThat(billingDocumentDTO.getRecipientSepaMandateReference(), is("REF2222"));
        assertThat(billingDocumentDTO.getRecipientSepaMandateIssueDate(),
                is( LocalDate.of(2022,12, 31)));
        assertThat(billingDocumentDTO.getRecipientBankName(), is("Postsparkasse"));
        assertThat(billingDocumentDTO.getRecipientBankOwner(), is("Fridolin Fröhlich"));
        assertThat(billingDocumentDTO.getRecipientBankIban(), is("AT01-2222-2222-2222"));
    }

    boolean assertIsInvoiceSonneGmbH(BillingDocumentDTO billingDocumentDTO,
                                     List<BillingDocumentItem> billingDocumentItemList,
                                     boolean isPreview) {
        if (isPreview) {
            assertThat(billingDocumentDTO.getDocumentNumber(), nullValue());
        } else {
            assertThat(billingDocumentDTO.getDocumentNumber(), startsWith("TRECH"));
        }
        assertIssuerDataValid(billingDocumentDTO);
        assertSonneGmbHDataValid(billingDocumentDTO);
        assertThat(billingDocumentDTO.getClearingPeriodType(), is("QUARTERLY"));
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("Abr_YQ-2023-3"));
        assertThat(billingDocumentDTO.getGrossAmountInEuro(), comparesEqualTo(BigDecimal.valueOf(35.88)));
        assertThat(billingDocumentDTO.getNetAmountInEuro(), comparesEqualTo(BigDecimal.valueOf(29.90)));
        // participant fee 10.00 at 20 % (2.00) + meter-point fee 19.90 at 20 % (3.98)
        assertThat(billingDocumentDTO.getVat1Percent(), comparesEqualTo(new BigDecimal("20")));
        assertThat(billingDocumentDTO.getVat1SumInEuro(), comparesEqualTo(new BigDecimal("5.98")));
        assertThat(billingDocumentDTO.getVat2Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat2SumInEuro(), nullValue());
        assertThat(billingDocumentItemList, hasSize(2));
        assertThat(billingDocumentItemList.get(0).getText(), is("Mitgliedsgebühr"));
        assertThat(billingDocumentItemList.get(1).getText(), startsWith("Zählpunktgebühr"));
        return true;
    }

    boolean assertIsInfoOrCreditNoteRcSonneGmbH(BillingDocumentDTO billingDocumentDTO,
                                                List<BillingDocumentItem> billingDocumentItemList,
                                                boolean createCreditNotesForAllProducers) {

        if (createCreditNotesForAllProducers) {
            assertThat(billingDocumentDTO.getBillingDocumentType(), is(BillingDocumentType.CREDIT_NOTE_RC));
            assertThat(billingDocumentDTO.getDocumentNumber(), startsWith("TGUT"));
        } else {
            assertThat(billingDocumentDTO.getBillingDocumentType(), is(BillingDocumentType.INFO));
            assertThat(billingDocumentDTO.getDocumentNumber(), is("-"));
        }
        assertIssuerDataValid(billingDocumentDTO);
        assertSonneGmbHDataValid(billingDocumentDTO);
        assertThat(billingDocumentDTO.getClearingPeriodType(), is("QUARTERLY"));
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("Abr_YQ-2023-3"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getGrossAmountInEuro()), is("762,55"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getNetAmountInEuro()), is("693,23"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getVat1Percent()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getVat1SumInEuro()), is("69,32"));
        assertThat(billingDocumentDTO.getVat2Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat2SumInEuro(), nullValue());
        assertThat(billingDocumentItemList, hasSize(2));
        var item0 = billingDocumentItemList.get(0);
        assertThat(item0.getText(), allOf(
                containsString("Anlage-Name: PV Sonne GmbH"),
                containsString("Anlage-Nr.: Anlagenr 3333"),
                containsString("P0000000000000000000003333")
        ));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getAmount()), is("3355,33"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getPricePerUnit()), is("7,77"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getNetValue()), is("260,71"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getVatPercent()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getVatValueInEuro()), is("26,07"));

        var item1 = billingDocumentItemList.get(1);
        assertThat(item1.getText(), allOf(
                containsString("Anlage-Name: PV Sonne GmbH"),
                containsString("Anlage-Nr.: Anlagenr 4444"),
                containsString("P0000000000000000000004444")
        ));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getAmount()), is("4477,45"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getPricePerUnit()), is("9,66"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getNetValue()), is("432,52"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getVatPercent()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getVatValueInEuro()), is("43,25"));

        return true;
    }

    boolean assertIsInvoiceGlueck(BillingDocumentDTO billingDocumentDTO,
                                  List<BillingDocumentItem> billingDocumentItemList,
                                  boolean isPreview) {
        if (isPreview) {
            assertThat(billingDocumentDTO.getDocumentNumber(), nullValue());
        } else {
            assertThat(billingDocumentDTO.getDocumentNumber(), startsWith("TRECH"));
        }
        assertIssuerDataValid(billingDocumentDTO);
        assertFelixGlueckDataValid(billingDocumentDTO);
        assertThat(billingDocumentDTO.getClearingPeriodType(), is("QUARTERLY"));
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("Abr_YQ-2023-3"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getGrossAmountInEuro()), is("125,21"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getNetAmountInEuro()), is("125,21"));
        assertThat(billingDocumentDTO.getVat1Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat1SumInEuro(), nullValue());
        assertThat(billingDocumentDTO.getVat2Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat2SumInEuro(), nullValue());
        assertThat(billingDocumentItemList, hasSize(3));

        BillingDocumentItem item2234 = billingDocumentItemList.stream()
                .filter(item -> item.getText().contains("2234")).findFirst().get();
        assertThat(item2234.getText(), allOf(
                containsString("Anlage-Name: Anlage Fix-Foxi"),
                containsString("Anlage-Nr.: Anlagenr 2234"))
        );
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item2234.getAmount()), is("777,60"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item2234.getPricePerUnit()), is("12,83"));
        assertThat(item2234.getPpuUnit(), nullValue()); // left empty (default is kWh)
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item2234.getNetValue()), is("99,77"));
        assertThat(item2234.getVatPercent(), is(BigDecimal.ZERO.setScale(1)));
        assertThat(item2234.getVatValueInEuro(), is(BigDecimal.ZERO));

        BillingDocumentItem item1234 = billingDocumentItemList.stream()
                .filter(item -> item.getText().contains("C0000000000000000000001234")).findFirst().get();
        assertThat(item1234.getText(), allOf(
                containsString("Anlage-Name: Anlage Foo-Bar"),
                containsString("Anlage-Nr.: Anlagenr 1234"))
        );
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1234.getAmount()), is("120,35"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1234.getPricePerUnit()), is("12,83"));
        assertThat(item1234.getPpuUnit(), nullValue()); // left empty (default is kWh)
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1234.getNetValue()), is("15,44"));
        assertThat(item1234.getVatPercent(), is(BigDecimal.ZERO.setScale(1)));
        assertThat(item1234.getVatValueInEuro(), is(BigDecimal.ZERO));

        BillingDocumentItem itemMitgliedsgebuehr = billingDocumentItemList.stream()
                .filter(item -> item.getText().contains("Mitgliedsgebühr")).findFirst().get();
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(itemMitgliedsgebuehr.getAmount()), is("1,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(itemMitgliedsgebuehr.getPricePerUnit()), is("10,00"));
        assertThat(itemMitgliedsgebuehr.getPpuUnit(), is("€"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(itemMitgliedsgebuehr.getNetValue()), is("10,00"));
        assertThat(itemMitgliedsgebuehr.getVatPercent(), is(BigDecimal.ZERO.setScale(1)));
        assertThat(itemMitgliedsgebuehr.getVatValueInEuro(), is(BigDecimal.ZERO));

        return true;
    }

    boolean assertIsInvoiceFroehlich(BillingDocumentDTO billingDocumentDTO,
                                      List<BillingDocumentItem> billingDocumentItemList,
                                      boolean isPreview) {
        if (isPreview) {
            assertThat(billingDocumentDTO.getDocumentNumber(), nullValue());
        } else {
            assertThat(billingDocumentDTO.getDocumentNumber(), startsWith("TRECH"));
        }
        assertIssuerDataValid(billingDocumentDTO);
        assertFridolinFroehlichDataValid(billingDocumentDTO);
        assertThat(billingDocumentDTO.getClearingPeriodType(), is("QUARTERLY"));
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("Abr_YQ-2023-3"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getGrossAmountInEuro()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getNetAmountInEuro()), is("10,00"));
        assertThat(billingDocumentDTO.getVat1Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat1SumInEuro(), nullValue());
        assertThat(billingDocumentDTO.getVat2Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat2SumInEuro(), nullValue());
        assertThat(billingDocumentItemList, hasSize(1));
        BillingDocumentItem itemMitgliedsgebuehr = billingDocumentItemList.get(0);
        assertThat(itemMitgliedsgebuehr.getText(), is("Mitgliedsgebühr"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(itemMitgliedsgebuehr.getAmount()), is("1,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(itemMitgliedsgebuehr.getPricePerUnit()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(itemMitgliedsgebuehr.getNetValue()), is("10,00"));
        assertThat(itemMitgliedsgebuehr.getVatPercent(), is(BigDecimal.ZERO.setScale(1)));
        assertThat(itemMitgliedsgebuehr.getVatValueInEuro(), is(BigDecimal.ZERO));

        return true;
    }

    boolean assertIsCreditNoteFroehlich(BillingDocumentDTO billingDocumentDTO,
                                   List<BillingDocumentItem> billingDocumentItemList,
                                   boolean isPreview) {
        if (isPreview) {
            assertThat(billingDocumentDTO.getDocumentNumber(), nullValue());
        } else {
            assertThat(billingDocumentDTO.getDocumentNumber(), startsWith("TGUT"));
        }
        assertIssuerDataValid(billingDocumentDTO);
        assertFridolinFroehlichDataValid(billingDocumentDTO);
        assertThat(billingDocumentDTO.getClearingPeriodType(), is("QUARTERLY"));
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("Abr_YQ-2023-3"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getGrossAmountInEuro()), is("431,68"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getNetAmountInEuro()), is("431,68"));
        assertThat(billingDocumentDTO.getVat1Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat1SumInEuro(), nullValue());
        assertThat(billingDocumentDTO.getVat2Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat2SumInEuro(), nullValue());
        assertThat(billingDocumentItemList, hasSize(1));

        BillingDocumentItem item = billingDocumentItemList.get(0);
        assertThat(item.getText(), allOf(
                containsString("Anlage-Name: PV Oberweg"),
                containsString("Anlage-Nr.: Anlagenr 2222"),
                containsString("P0000000000000000000002222")
        ));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item.getAmount()), is("2233,22"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item.getPricePerUnit()), is("19,33"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item.getNetValue()), is("431,68"));
        assertThat(item.getVatPercent(), is(BigDecimal.ZERO.setScale(1)));
        assertThat(item.getVatValueInEuro(), is(BigDecimal.ZERO));

        return true;
    }

    void assertDoBillingResults(DoBillingResults doBillingResults) {
        for(ParticipantAmount participantAmount : doBillingResults.getParticipantAmounts()) {
            switch(participantAmount.getMeteringPoints().get(0).getId()) {
                case "C0000000000000000000001234", "C0000000000000000000002234" -> /* Glück */ {
                    assertThat(participantAmount.getParticipantFee(), comparesEqualTo((BigDecimal.valueOf(10))));
                    assertThat(participantAmount.getAmount(), comparesEqualTo((BigDecimal.valueOf(115.21))));
                }
                case "P0000000000000000000002222" -> /* Fröhlich */ {
                    assertThat(participantAmount.getParticipantFee(), comparesEqualTo((BigDecimal.valueOf(10))));
                    assertThat(participantAmount.getAmount(), comparesEqualTo(BigDecimal.valueOf(431.68)));
                }
                case "P0000000000000000000003333", "P0000000000000000000004444" -> /* Sonne */ {
                    assertThat(participantAmount.getParticipantFee(), comparesEqualTo(BigDecimal.valueOf(12)));
                    assertThat(participantAmount.getMeteringPointFeeSum(), comparesEqualTo(BigDecimal.valueOf(23.88)));
                }
                default -> throw new AssertionFailedError("Unexpected meteringPointId found");
            }
        }
    }

    static final String RESULT_FINAL_OK = "Abrechnung : erfolgreich abgeschlossen.";
    static final String RESULT_PREVIEW_OK = "Abrechnung (Vorschau): erfolgreich abgeschlossen.";

    DoBillingParams params(boolean preview, LocalDate documentDate) {
        DoBillingParams doBillingParams = new DoBillingParams();
        doBillingParams.setTenantId("TE100100");
        doBillingParams.setClearingPeriodType("QUARTERLY");
        doBillingParams.setClearingPeriodIdentifier("Abr_YQ-2023-3");
        doBillingParams.setPreview(preview);
        doBillingParams.setClearingDocumentDate(documentDate);
        ArrayList<Allocation> allocations = new ArrayList<>();
        for (String[] meteringPointData : TEST_ALLOCATIONS) {
            Allocation allocation = new Allocation();
            allocation.setParticipantId(meteringPointData[0]);
            allocation.setMeteringPoint(meteringPointData[1]);
            allocation.setAllocationKWh(new BigDecimal(meteringPointData[2]));
            allocations.add(allocation);
        }
        doBillingParams.setAllocations(allocations.toArray(new Allocation[0]));
        return doBillingParams;
    }

    void assertRun(DoBillingResults doBillingResults, BillingRunStatus status) {
        assertThat(doBillingResults.getAbstractText(),
                is(status == BillingRunStatus.NEW ? RESULT_PREVIEW_OK : RESULT_FINAL_OK));
        assertThat(doBillingResults.getBillingRunId(), notNullValue());
        assertThat(doBillingResults.getParticipantAmounts().size(), is(3));
        assertDoBillingResults(doBillingResults);
        BillingRunDTO billingRunDTO = billingRunService.get(doBillingResults.getBillingRunId());
        // numberOfInvoices/numberOfCreditNotes are never set by the service: known-errors #33
        assertThat(billingRunDTO, allOf(
                hasProperty("tenantId", is("TE100100")),
                hasProperty("runStatus", is(status))
        ));
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServicePreview() {
        createBillingConfig(false);
        LocalDate runDay = LocalDate.now();
        DoBillingResults doBillingResults = billingService.doBilling(params(true, null));
        assertRun(doBillingResults, BillingRunStatus.NEW);
        assertBillingRunValid(doBillingResults.getBillingRunId(), true, null, runDay, false);
        storeDocuments("testBillingServicePreview");
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServiceFinal() {
        createBillingConfig(false);
        LocalDate runDay = LocalDate.now();
        DoBillingResults doBillingResults = billingService.doBilling(params(false, null));
        assertRun(doBillingResults, BillingRunStatus.DONE);
        assertBillingRunValid(doBillingResults.getBillingRunId(), false, null, runDay, false);
        storeDocuments("testBillingServiceFinal");
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServiceFinalWithDocumentDate()  {
        createBillingConfig(false);
        LocalDate documentDate = LocalDate.parse("2022-12-31");
        DoBillingResults doBillingResults = billingService.doBilling(params(false, documentDate));
        assertRun(doBillingResults, BillingRunStatus.DONE);
        assertBillingRunValid(doBillingResults.getBillingRunId(), false, documentDate, null, false);
        storeDocuments("testBillingServiceFinalWithDocumentDate");
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServiceFinalWithDocumentDate_reverseChargeCreditNotes()  {
        createBillingConfig(true);
        LocalDate documentDate = LocalDate.parse("2022-12-31");
        DoBillingResults doBillingResults = billingService.doBilling(params(false, documentDate));
        assertRun(doBillingResults, BillingRunStatus.DONE);
        assertBillingRunValid(doBillingResults.getBillingRunId(), false, documentDate, null, true);
        storeDocuments("testBillingServiceFinalWithDocumentDate_reverseChargeCreditNotes");
    }

    /** T2: the export lists the five documents and nine items with the gross sum of the run (1365.32). */
    @Test
    @Sql("/billing_master_data.sql")
    void testBillingXlsxService() throws IOException {
        createBillingConfig(false);
        DoBillingResults doBillingResults = billingService.doBilling(params(false, LocalDate.parse("2023-12-31")));
        assertRun(doBillingResults, BillingRunStatus.DONE);

        byte[] xlsx = billingDocumentXlsxService.createXlsx(doBillingResults.getBillingRunId());

        assertThat(DocumentReaders.header(xlsx, "Liste", 23), is("Rechnungsbetrag Brutto"));
        assertThat(DocumentReaders.dataRows(xlsx, "Liste"), is(5));
        assertThat(DocumentReaders.columnSum(xlsx, "Liste", 23), comparesEqualTo(new BigDecimal("1365.32")));
        assertThat(DocumentReaders.header(xlsx, "Details", 28), is("Pos. Bruttobetrag"));
        assertThat(DocumentReaders.dataRows(xlsx, "Details"), is(9));
        assertThat(DocumentReaders.columnSum(xlsx, "Details", 28), comparesEqualTo(new BigDecimal("1365.32")));

        if (!StringUtils.isEmpty(testAppProperties.getStoreDocumentsPath())) {
            String path = testAppProperties.getStoreDocumentsPath();
            path = (path.endsWith("/") ? path : path+"/") + "testBillingXlsxService_" +
                    new java.util.Date().getTime()+ ".xlsx";
            Files.write(Paths.get(path), xlsx);
        }
    }

    // The archive is tested in scenario.BillingScenarioOutputTests.archiveHoldsOnePdfPerDocumentOfTheRun.

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    void storeDocuments(@NotNull String prefix) {
        if (StringUtils.isEmpty(testAppProperties.getStoreDocumentsPath())) return;
        List<FileData> fileDataList = fileDataRepository.findAll();
        String path = testAppProperties.getStoreDocumentsPath();
        path = (path.endsWith("/") ? path : path+"/") + prefix + "_";
        for (FileData fileData : fileDataList) {
            try {
                if (fileData.getMimeType().contains("pdf")) {
                    Files.write(Paths.get(path + fileData.getName() + "_" + fileData.getId()), fileData.getData());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
