package org.vfeeg.eegfaktura.tools;

import net.datafaker.Faker;
import org.vfeeg.eegfaktura.billing.domain.*;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

public class MassDataGenerator {

    private static long documentNumber = 1L;

    /**
     * The main method is the entry point of the program. It generates fake billing masterdata and billing configurations,
     * and creates a CSV file containing the generated data.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {

        Faker faker = new Faker();

        ArrayList<BillingMasterdata> billingMasterdataList = new ArrayList<>();
        ArrayList< BillingConfig> billingConfigList = new ArrayList<>();

        for (int eegIdx = 1; eegIdx <= 1000; eegIdx++) {
            var eecName = "EEG "+faker.greekPhilosopher().name();
            var eecId = String.format("RC10%04d", eegIdx);
            boolean isSubjectToVat = Math.random() < 0.33;
            boolean useMeteringPointFee = Math.random() < 0.25;
            var billingMasterdata = BillingMasterdata.builder()
                            .id(UUID.randomUUID().toString())
                            .tenantId(eecId)
                            .eecName(eecName)
                            .eecId(eecId)
                            .eecBankIban(faker.finance().iban("AT"))
                            .eecBankName("Bank "+faker.funnyName().name())
                            .eecBankOwner(eecName)
                            .eecCity(faker.address().city())
                            .eecZipCode(Integer.toString((int)(Math.random()*8999)+1000))
                            .eecStreet(faker.address().streetAddress())
                            .eecPhone(faker.phoneNumber().phoneNumberInternational())
                            .eecCompanyRegisterNumber(isSubjectToVat ? eecId.replace("RC", "FC") : null)
                            .eecTaxId(isSubjectToVat ? eecId.replace("RC", "FIN") : null)
                            .eecVatId(isSubjectToVat ? eecId.replace("RC", "UID") : null)
                            .eecSubjectToVat(isSubjectToVat)
                            .tariffType("Tariftyp")
                            .tariffName("Tarif "+faker.starTrek().character())
                            .tariffText(isSubjectToVat ? "Tarif enthält 20% UST" : null)
                            .tariffBillingPeriod("")
                            .tariffUseVat(false)
                            .tariffVatInPercent(BigDecimal.ZERO)
                            .tariffParticipantFee(BigDecimal.ZERO)
                            .tariffParticipantFeeName("Mitgliedsgebühr")
                            .tariffParticipantFeeText(null)
                            .tariffParticipantFeeUseVat(isSubjectToVat)
                            .tariffParticipantFeeVatInPercent(new BigDecimal("20"))
                            .tariffParticipantFeeDiscount(BigDecimal.ZERO)
                            .tariffBasicFee(BigDecimal.ZERO)
                            .tariffDiscount(BigDecimal.ZERO)
                            .tariffWorkingFeePerConsumedkwh(BigDecimal.valueOf((long)(Math.random()*20)+5))
                            .tariffCreditAmountPerProducedkwh(BigDecimal.valueOf((long)(Math.random()*10)+5))
                            .tariffFreekwh(BigDecimal.ZERO)
                            .tariffUseMeteringPointFee(useMeteringPointFee)
                            .tariffMeteringPointFee(useMeteringPointFee ? BigDecimal.valueOf((long)(Math.random()*10)+5): null)
                            .tariffMeteringPointFeeText(null)
                            .build();

                BillingConfig billingConfig = BillingConfig.builder()
                        .id(UUID.randomUUID())
                        .tenantId(eecId)
                        .isCreateCreditNotesForAllProducers(true)
                        .beforeItemsTextInvoice(faker.lorem().sentence(10,5))
                        .beforeItemsTextCreditNote(faker.lorem().sentence(10,5))
                        .beforeItemsTextInfo(faker.lorem().sentence(10,5))
                        .afterItemsTextInvoice(faker.lorem().sentence(10,5))
                        .afterItemsTextCreditNote(faker.lorem().sentence(10,5))
                        .afterItemsTextInfo(faker.lorem().sentence(10,5))
                        .termsTextInvoice(faker.lorem().sentence(10,5))
                        .termsTextCreditNote(faker.lorem().sentence(10,5))
                        .termsTextInfo(faker.lorem().sentence(10,5))
                        .footerText(faker.lorem().sentence(10,5))
                        .documentNumberSequenceLength((int) (Math.random()*5)+3)
                        .invoiceNumberPrefix("RE")
                        .invoiceNumberStart(42L)
                        .creditNoteNumberPrefix("GT")
                        .creditNoteNumberStart(73L)
                        .dateCreated(OffsetDateTime.now())
                        .lastUpdated(OffsetDateTime.now())
                        .build();
                billingConfigList.add(billingConfig);

            for (int participantIdx = 1, max = getRandomParticipantNumber(); participantIdx <= max; participantIdx++) {
                String participantId = String.format("%04d%04d", eegIdx, participantIdx);
                String firstName = faker.name().firstName();
                String lastName = faker.name().lastName();
                boolean pIsCompany = Math.random() < 0.15;
                var pBillingMasterdata = billingMasterdata.toBuilder()
                        .participantId(UUID.randomUUID().toString())
                        .tenantId(billingConfig.getTenantId())
                        .participantTitleBefore(faker.name().prefix())
                        .participantFirstname(firstName)
                        .participantLastname(lastName)
                        .participantTitleAfter(faker.name().suffix())
                        .participantCompanyRegisterNumber(pIsCompany ? "FC"+participantId : null)
                        .participantTaxId(pIsCompany ? "FIN"+participantId : null)
                        .participantVatId(pIsCompany ? "UID"+participantId : null)
                        .participantEmail(firstName.toLowerCase() + "."
                                + lastName.toLowerCase() + "@foobarinc.at")
                        .participantStreet(faker.address().streetAddressNumber())
                        .participantZipCode(billingMasterdata.getEecZipCode())
                        .participantCity(billingMasterdata.getEecCity())
                        .participantNumber(participantId)
                        .participantBankName("Bank "+faker.funnyName().name())
                        .participantBankIban(faker.finance().iban("AT"))
                        .participantBankOwner(billingMasterdata.getParticipantLastname())
                        .participantSepaMandateReference("SEPA"+participantId)
                        .participantSepaMandateIssueDate(LocalDate.now()
                                .minusDays((long)(Math.random()*1000)+1))
                        .build();

                for (int meteringPointIdx = 1; meteringPointIdx <= (int) (Math.random() * 3) + 1; meteringPointIdx++) {
                    String meteringPointId = String.format("%04d%04d%01d", eegIdx, participantIdx, meteringPointIdx);
                    boolean isConsumer = Math.random() < 0.66;
                    var mpBillingMasterdata = pBillingMasterdata.toBuilder()
                            .meteringPointType(isConsumer ? MeteringPointType.CONSUMER : MeteringPointType.PRODUCER)
                            .meteringPointId((isConsumer ? "VERB" : "ERZ" )+"1234000000000"+meteringPointId)
                            .equipmentNumber(meteringPointId)
                            .meteringEquipmentName(faker.company().name().replace(',', '-')
                                            + (isConsumer ? " Verbraucher" : " Anlage Süd"))
                            .build();
                    billingMasterdataList.add(mpBillingMasterdata);
                }
            }
        }

        ArrayList<BillingRun> billingRunList = new ArrayList<>();
        ArrayList<BillingDocument> billingDocumentList = new ArrayList<>();
        ArrayList<BillingDocumentItem> billingDocumentItemList = new ArrayList<>();

        int eegCount = 0;
        int numberOfEegs = billingConfigList.size();

        for (BillingConfig billingConfig : billingConfigList) {

            int progress = (int)(eegCount++ * 1.0 / numberOfEegs * 10.0);
            System.out.printf("Generating Data %d%% [%s%s]\r", progress*10,
                    "=".repeat(progress), " ".repeat(10-progress));

            for (int i=0; i<12; i++) {
                LocalDate runStatusDate = LocalDate.now().minusMonths(13-i);
                LocalDateTime runStatusDateTime = runStatusDate.atTime(8,0,0);
                ZoneId zoneId = ZoneId.systemDefault();
                OffsetDateTime offsetDateTime = runStatusDateTime.atZone(zoneId).toOffsetDateTime();
                String clearingPeriodIdentifier = String.format("%s%02d%02d", billingConfig.getInvoiceNumberPrefix(),
                        runStatusDate.getYear(), runStatusDate.getMonthValue());
                BillingRun billingRun = BillingRun.builder()
                        .id(UUID.randomUUID())
                        .tenantId(billingConfig.getTenantId())
                        .clearingPeriodType("MONTHLY")
                        .clearingPeriodIdentifier(clearingPeriodIdentifier)
                        .runStatusDateTime(runStatusDateTime)
                        .runStatus(BillingRunStatus.DONE)
                        .dateCreated(offsetDateTime)
                        .lastUpdated(offsetDateTime)
                        .build();
                billingRunList.add(billingRun);

                HashMap<UUID, List<BillingMasterdata>> participantInvoices = new HashMap<>();
                HashMap<UUID, List<BillingMasterdata>> participantCreditNotes = new HashMap<>();

                Comparator<BillingMasterdata> participantAndMeteringPointComparator = Comparator
                        .comparing(BillingMasterdata::getParticipantId)
                        .thenComparing(BillingMasterdata::getMeteringPointId);

                billingMasterdataList.stream()
                        .filter(billingMasterdata -> billingMasterdata.getTenantId().equals(billingConfig.getTenantId()))
                        .sorted(participantAndMeteringPointComparator)
                        .forEach(billingMasterdata -> {
                            var participantId = UUID.fromString(billingMasterdata.getParticipantId());
                            var isConsumer = billingMasterdata.getMeteringPointType().equals(MeteringPointType.CONSUMER);
                            var list = isConsumer ? participantInvoices.get(participantId)
                                    : participantCreditNotes.get(participantId);
                            if (list == null) {
                                list = new ArrayList<>();
                                if (isConsumer)
                                    participantInvoices.put(participantId, list);
                                else
                                    participantCreditNotes.put(participantId, list);
                            }
                            list.add(billingMasterdata);
                        });

                participantInvoices.values().forEach(
                        masterdataList -> createDocumentAndItems(
                                billingDocumentList,
                                billingDocumentItemList,
                                masterdataList,
                                billingRun,
                                billingConfig,
                                BillingDocumentType.INVOICE)
                );
                participantInvoices.values().forEach(
                        masterdataList -> createDocumentAndItems(
                                billingDocumentList,
                                billingDocumentItemList,
                                masterdataList,
                                billingRun,
                                billingConfig,
                                BillingDocumentType.CREDIT_NOTE)
                );

            }
        }

        System.out.println("Generating Data 100% [==========] done.");
        System.out.println("Data generation complete. Writing csv files:");
        writeBillingMasterdataCsv(billingMasterdataList);
        writeBillingConfigCsv(billingConfigList);
        writeBillingRunCsv(billingRunList);
        writeBillingDocumentCsv(billingDocumentList);
        writeBillingDocumentItemCsv(billingDocumentItemList);

    }

    private static void createDocumentAndItems(ArrayList<BillingDocument> billingDocumentList,
                                               ArrayList<BillingDocumentItem> billingDocumentItemList,
                                               List<BillingMasterdata> masterdataList,
                                               BillingRun billingRun,
                                               BillingConfig billingConfig,
                                               BillingDocumentType billingDocumentType) {

        boolean isInvoice = billingDocumentType.equals(BillingDocumentType.INVOICE);

        BillingDocument billingDocument = BillingDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(billingConfig.getTenantId())
                .billingDocumentType(billingDocumentType)
                .documentNumber(String.format("%s%06d",
                        isInvoice ? billingConfig.getInvoiceNumberPrefix() : billingConfig.getCreditNoteNumberPrefix()
                        , documentNumber++))
                .billingRun(billingRun)
                .clearingPeriodType(billingRun.getClearingPeriodType())
                .clearingPeriodIdentifier(billingRun.getClearingPeriodIdentifier())
                .documentDate(billingRun.getRunStatusDateTime().toLocalDate())
                .beforeItemsText(isInvoice ? billingConfig.getBeforeItemsTextInvoice() : billingConfig.getBeforeItemsTextCreditNote())
                .afterItemsText(isInvoice ? billingConfig.getAfterItemsTextInvoice() : billingConfig.getAfterItemsTextCreditNote())
                .termsText(isInvoice ? billingConfig.getTermsTextInvoice() : billingConfig.getTermsTextCreditNote())
                .footerText(billingConfig.getFooterText())
                .participantId(masterdataList.get(0).getParticipantId())
                .recipientName(masterdataList.get(0).getParticipantLastname())
                .issuerName(masterdataList.get(0).getEecName())
                .vat1Percent(BigDecimal.ZERO)
                .vat1SumInEuro(BigDecimal.ZERO)
                .vat2Percent(BigDecimal.ZERO)
                .vat2SumInEuro(BigDecimal.ZERO)
                .netAmountInEuro(BigDecimal.ZERO)
                .grossAmountInEuro(BigDecimal.ZERO)
                .dateCreated(billingRun.getDateCreated())
                .lastUpdated(billingRun.getDateCreated())
                .build();
        billingDocumentList.add(billingDocument);
        masterdataList.forEach(
            masterdata -> {
                BillingDocumentItem billingDocumentItem = BillingDocumentItem.builder()
                        .id(UUID.randomUUID())
                        .tenantId(billingConfig.getTenantId())
                        .billingDocument(billingDocument)
                        .meteringPointType(isInvoice ? MeteringPointType.CONSUMER : MeteringPointType.PRODUCER)
                        .text(masterdata.getMeteringPointId())
                        .amount(BigDecimal.valueOf((long) (Math.random()*100+50)))
                        .pricePerUnit(isInvoice ? masterdata.getTariffWorkingFeePerConsumedkwh()
                                : masterdata.getTariffCreditAmountPerProducedkwh())
                        .unit("kWh")
                        .dateCreated(billingRun.getDateCreated())
                        .lastUpdated(billingRun.getDateCreated())
                        .build();
                billingDocumentItemList.add(billingDocumentItem);
        });

    }

    private static void writeBillingMasterdataCsv(ArrayList<BillingMasterdata> billingMasterdataList) {

        StringBuilder sb = new StringBuilder();
        try (PrintWriter writer = new PrintWriter("billing_masterdata.csv")) {
            sb.append("tenant_id,eec_name,eec_id,eec_bank_iban,eec_bank_name,eec_bank_owner,eec_city," +
                    "eec_zip_code,eec_street,eec_phone,eec_company_register_number,eec_tax_id,eec_vat_id," +
                    "eec_subject_to_vat,tariff_type,tariff_name,tariff_text,tariff_billing_period,tariff_use_vat," +
                    "tariff_vat_in_percent,tariff_participant_fee,tariff_participant_fee_name," +
                    "tariff_participant_fee_text,tariff_participant_fee_use_vat," +
                    "tariff_participant_fee_vat_in_percent,tariff_participant_fee_discount,tariff_basic_fee," +
                    "tariff_discount,tariff_working_fee_per_consumedkwh,tariff_credit_amount_per_producedkwh," +
                    "tariff_freekwh,tariff_use_metering_point_fee,tariff_metering_point_fee," +
                    "tariff_metering_point_fee_text,participant_id,participant_title_before," +
                    "participant_firstname,participant_lastname,participant_title_after," +
                    "participant_company_register_number,participant_tax_id,participant_vat_id," +
                    "participant_email,participant_street,participant_zip_code,participant_city," +
                    "participant_number,participant_bank_name,participant_bank_iban,participant_bank_owner," +
                    "participant_sepa_mandate_reference,participant_sepa_mandate_issue_date," +
                    "metering_point_type,metering_point_id,equipment_number,metering_equipment_name");
            sb.append("\n");

            for (BillingMasterdata billingMasterdata : billingMasterdataList) {
                sb.append(billingMasterdata.getTenantId());
                sb.append(",");
                sb.append(billingMasterdata.getEecName());
                sb.append(",");
                sb.append(billingMasterdata.getEecId());
                sb.append(",");
                sb.append(billingMasterdata.getEecBankIban());
                sb.append(",");
                sb.append(billingMasterdata.getEecBankName());
                sb.append(",");
                sb.append(billingMasterdata.getEecBankOwner());
                sb.append(",");
                sb.append(billingMasterdata.getEecCity());
                sb.append(",");
                sb.append(billingMasterdata.getEecZipCode());
                sb.append(",");
                sb.append(billingMasterdata.getEecStreet());
                sb.append(",");
                sb.append(billingMasterdata.getEecPhone());
                sb.append(",");
                sb.append(billingMasterdata.getEecCompanyRegisterNumber());
                sb.append(",");
                sb.append(billingMasterdata.getEecTaxId());
                sb.append(",");
                sb.append(billingMasterdata.getEecVatId());
                sb.append(",");
                sb.append(billingMasterdata.getEecSubjectToVat());
                sb.append(",");
                sb.append(billingMasterdata.getTariffType());
                sb.append(",");
                sb.append(billingMasterdata.getTariffName());
                sb.append(",");
                sb.append(billingMasterdata.getTariffText());
                sb.append(",");
                sb.append(billingMasterdata.getTariffBillingPeriod());
                sb.append(",");
                sb.append(billingMasterdata.getTariffUseVat());
                sb.append(",");
                sb.append(billingMasterdata.getTariffVatInPercent());
                sb.append(",");
                sb.append(billingMasterdata.getTariffParticipantFee());
                sb.append(",");
                sb.append(billingMasterdata.getTariffParticipantFeeName());
                sb.append(",");
                sb.append(billingMasterdata.getTariffParticipantFeeText());
                sb.append(",");
                sb.append(billingMasterdata.getTariffParticipantFeeUseVat());
                sb.append(",");
                sb.append(billingMasterdata.getTariffParticipantFeeVatInPercent());
                sb.append(",");
                sb.append(billingMasterdata.getTariffParticipantFeeDiscount());
                sb.append(",");
                sb.append(billingMasterdata.getTariffBasicFee());
                sb.append(",");
                sb.append(billingMasterdata.getTariffDiscount());
                sb.append(",");
                sb.append(billingMasterdata.getTariffWorkingFeePerConsumedkwh());
                sb.append(",");
                sb.append(billingMasterdata.getTariffCreditAmountPerProducedkwh());
                sb.append(",");
                sb.append(billingMasterdata.getTariffFreekwh());
                sb.append(",");
                sb.append(billingMasterdata.getTariffUseMeteringPointFee());
                sb.append(",");
                sb.append(billingMasterdata.getTariffMeteringPointFee());
                sb.append(",");
                sb.append(billingMasterdata.getTariffMeteringPointFeeText());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantId());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantTitleBefore());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantFirstname());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantLastname());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantTitleAfter());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantCompanyRegisterNumber());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantTaxId());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantVatId());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantEmail());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantStreet());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantZipCode());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantCity());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantNumber());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantBankName());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantBankIban());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantBankOwner());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantSepaMandateReference());
                sb.append(",");
                sb.append(billingMasterdata.getParticipantSepaMandateIssueDate());
                sb.append(",");
                sb.append(billingMasterdata.getMeteringPointType().ordinal());
                sb.append(",");
                sb.append(billingMasterdata.getMeteringPointId());
                sb.append(",");
                sb.append(billingMasterdata.getEquipmentNumber());
                sb.append(",");
                sb.append(billingMasterdata.getMeteringEquipmentName());
                sb.append("\n");

                writer.write(sb.toString());
                sb.setLength(0);
            }

        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("billing_masterdata.csv created successfully.");
    }

    private static void writeBillingConfigCsv(ArrayList<BillingConfig> billingConfigList) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_config.csv"))) {

            writer.println("id,tenant_id,create_credit_notes_for_all_producers,before_items_text_invoice," +
                    "before_items_text_credit_note,before_items_text_info,after_items_text_invoice," +
                    "after_items_text_credit_note,after_items_text_info,terms_text_invoice," +
                    "terms_text_credit_note,terms_text_info,footer_text,document_number_sequence_length," +
                    "invoice_number_prefix,invoice_number_start," +
                    "credit_note_number_prefix,credit_note_number_start, date_created, last_updated");

            StringBuilder sb = new StringBuilder();
            for (BillingConfig billingConfig : billingConfigList) {
                sb.append(billingConfig.getId()).append(",")
                        .append(billingConfig.getTenantId()).append(",")
                        .append(billingConfig.isCreateCreditNotesForAllProducers()).append(",")
                        .append(billingConfig.getBeforeItemsTextInvoice()).append(",")
                        .append(billingConfig.getBeforeItemsTextCreditNote()).append(",")
                        .append(billingConfig.getBeforeItemsTextInfo()).append(",")
                        .append(billingConfig.getAfterItemsTextInvoice()).append(",")
                        .append(billingConfig.getAfterItemsTextCreditNote()).append(",")
                        .append(billingConfig.getAfterItemsTextInfo()).append(",")
                        .append(billingConfig.getTermsTextInvoice()).append(",")
                        .append(billingConfig.getTermsTextCreditNote()).append(",")
                        .append(billingConfig.getTermsTextInfo()).append(",")
                        .append(billingConfig.getFooterText()).append(",")
                        .append(billingConfig.getDocumentNumberSequenceLength()).append(",")
                        .append(billingConfig.getInvoiceNumberPrefix()).append(",")
                        .append(billingConfig.getInvoiceNumberStart()).append(",")
                        .append(billingConfig.getCreditNoteNumberPrefix()).append(",")
                        .append(billingConfig.getCreditNoteNumberStart()).append(",")
                        .append(billingConfig.getDateCreated()).append(",")
                        .append(billingConfig.getLastUpdated())
                ;
                writer.println(sb);
                sb.setLength(0);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("billing_config.csv created successfully.");
    }

    private static void writeBillingDocumentItemCsv(ArrayList<BillingDocumentItem> billingDocumentItemList) {

        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_document_item.csv"))) {

            writer.println("id,tenant_id,clearing_period_type,clearing_period_identifier,amount,metering_point_id," +
                    "metering_point_type,text,document_text,unit,price_per_unit,ppu_unit,net_value,discount_percent," +
                    "vat_percent,vat_value_in_euro,gross_value,date_created,last_updated");

            StringBuilder sb = new StringBuilder();
            for (BillingDocumentItem billingDocumentItem : billingDocumentItemList) {
                sb.append(billingDocumentItem.getId()).append(",")
                        .append(billingDocumentItem.getTenantId()).append(",")
                        .append(billingDocumentItem.getClearingPeriodType()).append(",")
                        .append(billingDocumentItem.getClearingPeriodIdentifier()).append(",")
                        .append(billingDocumentItem.getAmount()).append(",")
                        .append(billingDocumentItem.getMeteringPointId()).append(",")
                        .append(billingDocumentItem.getMeteringPointType().ordinal()).append(",")
                        .append(billingDocumentItem.getText()).append(",")
                        .append(billingDocumentItem.getDocumentText()).append(",")
                        .append(billingDocumentItem.getUnit()).append(",")
                        .append(billingDocumentItem.getPricePerUnit()).append(",")
                        .append(billingDocumentItem.getPpuUnit()).append(",")
                        .append(billingDocumentItem.getNetValue()).append(",")
                        .append(billingDocumentItem.getDiscountPercent()).append(",")
                        .append(billingDocumentItem.getVatPercent()).append(",")
                        .append(billingDocumentItem.getVatValueInEuro()).append(",")
                        .append(billingDocumentItem.getGrossValue()).append(",")
                        .append(billingDocumentItem.getDateCreated()).append(",")
                        .append(billingDocumentItem.getLastUpdated());
                writer.println(sb);
                sb.setLength(0);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("billing_document_item.csv created successfully.");
    }

    private static void writeBillingDocumentCsv(ArrayList<BillingDocument> billingDocumentList) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_document.csv"))) {

            writer.println("id,document_number,document_date,status,clearing_period_type,before_items_text," +
                    "after_items_text,terms_text,footer_text,tenant_id,participant_id,recipient_name," +
                    "recipient_participant_number,recipient_address_line1,recipient_address_line2,recipient_address_line3," +
                    "recipient_bank_name,recipient_bank_iban,recipient_bank_owner,recipient_sepa_mandate_reference," +
                    "recipient_sepa_mandate_issue_date,recipient_email,recipient_tax_id,recipient_vat_id,issuer_name," +
                    "issuer_address_line1,issuer_address_line2,issuer_address_line3,issuer_phone,issuer_mail," +
                    "issuer_website,issuer_tax_id,issuer_vat_id,issuer_company_register_number,issuer_bank_name," +
                    "issuer_bankiban,issuer_bank_owner,vat1percent,vat1sum_in_euro,vat2percent,vat2sum_in_euro," +
                    "net_amount_in_euro,gross_amount_in_euro,clearing_period_identifier,billing_document_type," +
                    "date_created,last_updated");

            StringBuilder sb = new StringBuilder();
            for (BillingDocument billingDocument : billingDocumentList) {
                sb.append(billingDocument.getId()).append(",")
                    .append(billingDocument.getDocumentNumber()).append(",")
                    .append(billingDocument.getDocumentDate()).append(",")
                    .append(billingDocument.getStatus()).append(",")
                    .append(billingDocument.getClearingPeriodType()).append(",")
                    .append(billingDocument.getBeforeItemsText()).append(",")
                    .append(billingDocument.getAfterItemsText()).append(",")
                    .append(billingDocument.getTermsText()).append(",")
                    .append(billingDocument.getFooterText()).append(",")
                    .append(billingDocument.getTenantId()).append(",")
                    .append(billingDocument.getParticipantId()).append(",")
                    .append(billingDocument.getRecipientName()).append(",")
                    .append(billingDocument.getRecipientParticipantNumber()).append(",")
                    .append(billingDocument.getRecipientAddressLine1()).append(",")
                    .append(billingDocument.getRecipientAddressLine2()).append(",")
                    .append(billingDocument.getRecipientAddressLine3()).append(",")
                    .append(billingDocument.getRecipientBankName()).append(",")
                    .append(billingDocument.getRecipientBankIban()).append(",")
                    .append(billingDocument.getRecipientBankOwner()).append(",")
                    .append(billingDocument.getRecipientSepaMandateReference()).append(",")
                    .append(billingDocument.getRecipientSepaMandateIssueDate()).append(",")
                    .append(billingDocument.getRecipientEmail()).append(",")
                    .append(billingDocument.getRecipientTaxId()).append(",")
                    .append(billingDocument.getRecipientVatId()).append(",")
                    .append(billingDocument.getIssuerName()).append(",")
                    .append(billingDocument.getIssuerAddressLine1()).append(",")
                    .append(billingDocument.getIssuerAddressLine2()).append(",")
                    .append(billingDocument.getIssuerAddressLine3()).append(",")
                    .append(billingDocument.getIssuerPhone()).append(",")
                    .append(billingDocument.getIssuerMail()).append(",")
                    .append(billingDocument.getIssuerWebsite()).append(",")
                    .append(billingDocument.getIssuerTaxId()).append(",")
                    .append(billingDocument.getIssuerVatId()).append(",")
                    .append(billingDocument.getIssuerCompanyRegisterNumber()).append(",")
                    .append(billingDocument.getIssuerBankName()).append(",")
                    .append(billingDocument.getIssuerBankIBAN()).append(",")
                    .append(billingDocument.getIssuerBankOwner()).append(",")
                    .append(billingDocument.getVat1Percent()).append(",")
                    .append(billingDocument.getVat1SumInEuro()).append(",")
                    .append(billingDocument.getVat2Percent()).append(",")
                    .append(billingDocument.getVat2SumInEuro()).append(",")
                    .append(billingDocument.getNetAmountInEuro()).append(",")
                    .append(billingDocument.getGrossAmountInEuro()).append(",")
                    .append(billingDocument.getClearingPeriodIdentifier()).append(",")
                    .append(billingDocument.getBillingDocumentType().ordinal()).append(",")
                    .append(billingDocument.getDateCreated()).append(",")
                    .append(billingDocument.getLastUpdated());
                writer.println(sb);
                sb.setLength(0);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("billing_document.csv created successfully.");
    }

    private static void writeBillingRunCsv(ArrayList<BillingRun> billingRunList) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_run.csv"))) {

            writer.println("id,clearing_period_type,clearing_period_identifier,tenant_id,run_status," +
                    "run_status_date_time,mail_status,mail_status_date_time,sepa_status,sepa_status_date_time," +
                    "number_of_invoices,number_of_credit_notes,date_created,last_updated");

            StringBuilder sb = new StringBuilder();
            for (BillingRun billingRun : billingRunList) {
                sb.append(billingRun.getId()).append(",")
                        .append(billingRun.getClearingPeriodType()).append(",")
                        .append(billingRun.getClearingPeriodIdentifier()).append(",")
                        .append(billingRun.getTenantId()).append(",")
                        .append(billingRun.getRunStatus().ordinal()).append(",")
                        .append(billingRun.getRunStatusDateTime()).append(",")
                        .append(billingRun.getMailStatus()).append(",")
                        .append(billingRun.getMailStatusDateTime()).append(",")
                        .append(billingRun.getSepaStatus()).append(",")
                        .append(billingRun.getSepaStatusDateTime()).append(",")
                        .append(billingRun.getNumberOfInvoices()).append(",")
                        .append(billingRun.getNumberOfCreditNotes()).append(",")
                        .append(billingRun.getDateCreated()).append(",")
                        .append(billingRun.getLastUpdated());
                writer.println(sb);
                sb.setLength(0);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("billing_run.csv created successfully.");
    }

    private static int getRandomParticipantNumber() {
        return switch ((int) (Math.random() * 10)) {
            case 1 -> (int) (Math.random() * 500) + 100;
            case 2, 3, 4 -> (int) (Math.random() * 50) + 10;
            default -> (int) (Math.random() * 10) + 5;
        };
    }

}
