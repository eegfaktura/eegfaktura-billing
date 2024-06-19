package org.vfeeg.eegfaktura.billing;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import junit.framework.AssertionFailedError;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
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
        {"C0000000000000000000001234", "12.34"},
        {"C0000000000000000000002234", "22.34"},
        {"P0000000000000000000002222", "22.22"},
        {"P0000000000000000000003333", "33.33"},
        {"P0000000000000000000004444", "44.44"}
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

    @Test
    void contextLoads() {
    }

    @Test
    void testContainer() {
       assertThat(postgreSQLContainer.isRunning(), is(true));
    }

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
                //@TODO: Die weiteren Properties prüfen
                hasProperty("eecName", is("Energiegemeinschaft Holy Grail")),
                hasProperty("eecId", is("TE100100")),
                hasProperty("tariffCreditAmountPerProducedkwh", is(BigDecimal.valueOf(19)))
        ));

    }

    void assertBillingRunValid(UUID billingRunId, boolean isPreview, LocalDate fixedDate,
                               boolean createCreditNotesForAllProducers) {

        List<BillingDocumentDTO> billingDocumentDTOList = billingDocumentService.findByBillingRunId(
                billingRunId);
        assertThat(billingDocumentDTOList, hasSize(5));

        for (BillingDocumentDTO billingDocumentDTO : billingDocumentDTOList) {

            assertThat(billingDocumentDTO.getDocumentDate(),
                    is(Objects.requireNonNullElseGet(fixedDate, LocalDate::now)));

            List<BillingDocumentItem> billingDocumentItemList = billingDocumentItemRepository
                    .findByBillingDocument_Id(billingDocumentDTO.getId());

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
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("2023-YQ-3"));
        assertThat(billingDocumentDTO.getGrossAmountInEuro(), comparesEqualTo(BigDecimal.valueOf(13.00)));
        assertThat(billingDocumentDTO.getNetAmountInEuro(), comparesEqualTo(BigDecimal.valueOf(12.50)));
        //assertThat(billingDocumentDTO.getVat1Percent(), is(BigDecimal.ZERO));
        //assertThat(billingDocumentDTO.getVat1SumInEuro(), is(BigDecima.ZERO));
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
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("2023-YQ-3"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getGrossAmountInEuro()), is("16,25"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getNetAmountInEuro()), is("14,78"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getVat1Percent()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getVat1SumInEuro()), is("1,48"));
        assertThat(billingDocumentDTO.getVat2Percent(), nullValue());
        assertThat(billingDocumentDTO.getVat2SumInEuro(), nullValue());
        assertThat(billingDocumentItemList, hasSize(2));
        var item0 = billingDocumentItemList.get(0);
        assertThat(item0.getText(), allOf(
                containsString("Anlage-Name: PV Sonne GmbH"),
                containsString("Anlage-Nr.: Anlagenr 3333"),
                containsString("P0000000000000000000003333")
        ));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getAmount()), is("33,33"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getPricePerUnit()), is("19,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getNetValue()), is("6,33"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getVatPercent()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item0.getVatValueInEuro()), is("0,63"));

        var item1 = billingDocumentItemList.get(1);
        assertThat(item1.getText(), allOf(
                containsString("Anlage-Name: PV Sonne GmbH"),
                containsString("Anlage-Nr.: Anlagenr 4444"),
                containsString("P0000000000000000000004444")
        ));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getAmount()), is("44,44"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getPricePerUnit()), is("19,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getNetValue()), is("8,44"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getVatPercent()), is("10,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1.getVatValueInEuro()), is("0,84"));

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
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("2023-YQ-3"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getGrossAmountInEuro()), is("15,20"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getNetAmountInEuro()), is("15,20"));
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
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item2234.getAmount()), is("22,34"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item2234.getPricePerUnit()), is("15,00"));
        assertThat(item2234.getPpuUnit(), nullValue()); // left empty (default is kWh)
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item2234.getNetValue()), is("3,35"));
        assertThat(item2234.getVatPercent(), is(BigDecimal.ZERO.setScale(1)));
        assertThat(item2234.getVatValueInEuro(), is(BigDecimal.ZERO));

        BillingDocumentItem item1234 = billingDocumentItemList.stream()
                .filter(item -> item.getText().contains("C0000000000000000000001234")).findFirst().get();
        assertThat(item1234.getText(), allOf(
                containsString("Anlage-Name: Anlage Foo-Bar"),
                containsString("Anlage-Nr.: Anlagenr 1234"))
        );
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1234.getAmount()), is("12,34"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1234.getPricePerUnit()), is("15,00"));
        assertThat(item1234.getPpuUnit(), nullValue()); // left empty (default is kWh)
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item1234.getNetValue()), is("1,85"));
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
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("2023-YQ-3"));
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
        assertThat(billingDocumentDTO.getClearingPeriodIdentifier(), is("2023-YQ-3"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getGrossAmountInEuro()), is("4,22"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(billingDocumentDTO.getNetAmountInEuro()), is("4,22"));
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
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item.getAmount()), is("22,22"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item.getPricePerUnit()), is("19,00"));
        assertThat(BigDecimalTools.DECIMAL_FORMAT.format(item.getNetValue()), is("4,22"));
        assertThat(item.getVatPercent(), is(BigDecimal.ZERO.setScale(1)));
        assertThat(item.getVatValueInEuro(), is(BigDecimal.ZERO));

        return true;
    }

    void assertDoBillingResults(DoBillingResults doBillingResults) {
        for(ParticipantAmount participantAmount : doBillingResults.getParticipantAmounts()) {
            switch(participantAmount.getMeteringPoints().get(0).getId()) {
                case "C0000000000000000000001234", "C0000000000000000000002234" -> /* Glück */ {
                    assertThat(participantAmount.getParticipantFee(), comparesEqualTo((BigDecimal.valueOf(10))));
                    assertThat(participantAmount.getAmount(), comparesEqualTo((BigDecimal.valueOf(5.202))));
                }
                case "P0000000000000000000002222" -> /* Fröhlich */ {
                    assertThat(participantAmount.getParticipantFee(), comparesEqualTo((BigDecimal.valueOf(10))));
                    assertThat(participantAmount.getAmount(), comparesEqualTo(BigDecimal.valueOf(4.2218)));
                }
                case "P0000000000000000000003333" -> /* Sonne */ {
                    assertThat(participantAmount.getParticipantFee(), comparesEqualTo(BigDecimal.valueOf(10)));
                    assertThat(participantAmount.getMeteringPointFeeSum(), comparesEqualTo(BigDecimal.valueOf(3)));
                }
                default -> throw new AssertionFailedError("Unexpected meteringPointId found");
            }
        }
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServicePreview() {
        createBillingConfig(false);
        DoBillingParams doBillingParams = new DoBillingParams();
        doBillingParams.setTenantId("TE100100");
        doBillingParams.setClearingPeriodType("QUARTERLY");
        doBillingParams.setClearingPeriodIdentifier("2023-YQ-3");
        doBillingParams.setPreview(true);
        ArrayList<Allocation> allocations = new ArrayList<>();
        for (String[] meteringPointData : TEST_ALLOCATIONS) {
            Allocation allocation = new Allocation();
            allocation.setMeteringPoint(meteringPointData[0]);
            allocation.setAllocationKWh(BigDecimal.valueOf(Double.parseDouble(meteringPointData[1])));
            allocations.add(allocation);
        }
        doBillingParams.setAllocations(allocations.toArray(new Allocation[0]));
        DoBillingResults doBillingResults = billingService.doBilling(doBillingParams);
        assertThat(doBillingResults.getBillingRunId(), notNullValue());
        assertThat(doBillingResults.getParticipantAmounts().size(),
                is(3));
        assertDoBillingResults(doBillingResults);

        BillingRunDTO billingRunDTO = billingRunService.get(doBillingResults.getBillingRunId());
        assertThat(billingRunDTO, allOf(
                hasProperty("tenantId", is("TE100100")),
                hasProperty("runStatus", is(BillingRunStatus.NEW))
//@TODO: numberOfxxx not set at the moment include again when done
//                hasProperty("numberOfInvoices", is(3)),
//                hasProperty("numberOfCreditNotes", is(2))
        ));

        assertBillingRunValid(doBillingResults.getBillingRunId(), true, null, false);

        storeDocuments("testBillingServicePreview");
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServiceFinal() {
        createBillingConfig(false);
        DoBillingParams doBillingParams = new DoBillingParams();
        doBillingParams.setTenantId("TE100100");
        doBillingParams.setClearingPeriodType("QUARTERLY");
        doBillingParams.setClearingPeriodIdentifier("2023-YQ-3");
        doBillingParams.setPreview(false);
        ArrayList<Allocation> allocations = new ArrayList<>();

        for (String[] meteringPointData : TEST_ALLOCATIONS) {
            Allocation allocation = new Allocation();
            allocation.setMeteringPoint(meteringPointData[0]);
            allocation.setAllocationKWh(BigDecimal.valueOf(Double.parseDouble(meteringPointData[1])));
            allocations.add(allocation);
        }

        doBillingParams.setAllocations(allocations.toArray(new Allocation[0]));
        DoBillingResults doBillingResults = billingService.doBilling(doBillingParams);
        assertThat(doBillingResults.getBillingRunId(), notNullValue());
        assertThat(doBillingResults.getParticipantAmounts(), not(empty()));

        BillingRunDTO billingRunDTO = billingRunService.get(doBillingResults.getBillingRunId());
        assertThat(billingRunDTO, allOf(
                hasProperty("tenantId", is("TE100100")),
                hasProperty("runStatus", is(BillingRunStatus.DONE))
//@TODO: numberOfxxx not set at the moment include again when done
//                hasProperty("numberOfInvoices", is(3)),
//                hasProperty("numberOfCreditNotes", is(2))
        ));

        assertBillingRunValid(doBillingResults.getBillingRunId(), false, null, false);

        storeDocuments("testBillingServiceFinal");
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServiceFinalWithDocumentDate()  {
        createBillingConfig(false);
        LocalDate documentDate = LocalDate.parse("2022-12-31");
        DoBillingParams doBillingParams = new DoBillingParams();
        doBillingParams.setTenantId("TE100100");
        doBillingParams.setClearingPeriodType("QUARTERLY");
        doBillingParams.setClearingPeriodIdentifier("2023-YQ-3");
        doBillingParams.setPreview(false);
        doBillingParams.setClearingDocumentDate(documentDate);
        ArrayList<Allocation> allocations = new ArrayList<>();
        for (String[] meteringPointId : TEST_ALLOCATIONS) {
            Allocation allocation = new Allocation();
            allocation.setMeteringPoint(meteringPointId[0]);
            allocation.setAllocationKWh(BigDecimal.valueOf(Double.parseDouble(meteringPointId[1])));
            allocations.add(allocation);
        }
        doBillingParams.setAllocations(allocations.toArray(new Allocation[0]));
        DoBillingResults doBillingResults = billingService.doBilling(doBillingParams);
        assertThat(doBillingResults.getBillingRunId(), notNullValue());
        assertThat(doBillingResults.getParticipantAmounts(), not(empty()));

        BillingRunDTO billingRunDTO = billingRunService.get(doBillingResults.getBillingRunId());
        assertThat(billingRunDTO, allOf(
                hasProperty("tenantId", is("TE100100")),
                hasProperty("runStatus", is(BillingRunStatus.DONE))
//@TODO: numberOfxxx not set at the moment include again when done
//                hasProperty("numberOfInvoices", is(3)),
//                hasProperty("numberOfCreditNotes", is(2))
        ));

        assertBillingRunValid(doBillingResults.getBillingRunId(), false, documentDate,
                false);
        storeDocuments("testBillingServiceFinalWithDocumentDate");
    }

    @Test
    @Sql("/billing_master_data.sql")
    void testBillingServiceFinalWithDocumentDate_reverseChargeCreditNotes()  {
        createBillingConfig(true);
        LocalDate documentDate = LocalDate.parse("2022-12-31");
        DoBillingParams doBillingParams = new DoBillingParams();
        doBillingParams.setTenantId("TE100100");
        doBillingParams.setClearingPeriodType("QUARTERLY");
        doBillingParams.setClearingPeriodIdentifier("2023-YQ-3");
        doBillingParams.setPreview(false);
        doBillingParams.setClearingDocumentDate(documentDate);
        ArrayList<Allocation> allocations = new ArrayList<>();
        for (String[] meteringPointId : TEST_ALLOCATIONS) {
            Allocation allocation = new Allocation();
            allocation.setMeteringPoint(meteringPointId[0]);
            allocation.setAllocationKWh(BigDecimal.valueOf(Double.parseDouble(meteringPointId[1])));
            allocations.add(allocation);
        }
        doBillingParams.setAllocations(allocations.toArray(new Allocation[0]));
        DoBillingResults doBillingResults = billingService.doBilling(doBillingParams);
        assertThat(doBillingResults.getBillingRunId(), notNullValue());
        assertThat(doBillingResults.getParticipantAmounts(), not(empty()));

        BillingRunDTO billingRunDTO = billingRunService.get(doBillingResults.getBillingRunId());
        assertThat(billingRunDTO, allOf(
                hasProperty("tenantId", is("TE100100")),
                hasProperty("runStatus", is(BillingRunStatus.DONE))
//@TODO: numberOfxxx not set at the moment include again when done
//                hasProperty("numberOfInvoices", is(3)),
//                hasProperty("numberOfCreditNotes", is(2))
        ));

        assertBillingRunValid(doBillingResults.getBillingRunId(), false, documentDate,
                true);
        storeDocuments("testBillingServiceFinalWithDocumentDate_reverseChargeCreditNotes");
    }


    @Test
    @Sql("/billing_master_data.sql")
    void testBillingXlsxService() throws IOException {
        createBillingConfig(false);
        DoBillingParams doBillingParams = new DoBillingParams();
        doBillingParams.setTenantId("TE100100");
        doBillingParams.setClearingPeriodType("QUARTERLY");
        doBillingParams.setClearingPeriodIdentifier("2023-YQ-3");
        doBillingParams.setPreview(false);
        ArrayList<Allocation> allocations = new ArrayList<>();

        for (String[] meteringPointData : TEST_ALLOCATIONS) {
            Allocation allocation = new Allocation();
            allocation.setMeteringPoint(meteringPointData[0]);
            allocation.setAllocationKWh(BigDecimal.valueOf(Double.parseDouble(meteringPointData[1])));
            allocations.add(allocation);
        }

        doBillingParams.setAllocations(allocations.toArray(new Allocation[0]));
        DoBillingResults doBillingResults = billingService.doBilling(doBillingParams);
        assertThat(doBillingResults.getBillingRunId(), notNullValue());
        assertThat(doBillingResults.getParticipantAmounts(), not(empty()));

        ByteArrayResource resource = new ByteArrayResource(
                billingDocumentXlsxService.createXlsx(doBillingResults.getBillingRunId()));

        if (!StringUtils.isEmpty(testAppProperties.getStoreDocumentsPath())) {
            String path = testAppProperties.getStoreDocumentsPath();
            path = (path.endsWith("/") ? path : path+"/") + "testBillingXlsxService_" +
                    new java.util.Date().getTime()+ ".xlsx";
            Files.write(Paths.get(path), resource.getContentAsByteArray());
        }

    }


    // @TODO testBillingXlsxService
    // @TODO testBillingDocumentArchiveService

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
