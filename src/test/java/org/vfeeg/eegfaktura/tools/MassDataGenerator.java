package org.vfeeg.eegfaktura.tools;

import net.datafaker.Faker;
import org.vfeeg.eegfaktura.billing.domain.*;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class MassDataGenerator {

    private static long documentNumber = 1l;

    /**
     * The main method is the entry point of the program. It generates fake billing masterdata and billing configurations,
     * and creates a CSV file containing the generated data.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {

        Faker faker = new Faker();

        ArrayList<BillingMasterdata> billingMasterdataList = new ArrayList<BillingMasterdata>();
        ArrayList< BillingConfig> billingConfigList = new ArrayList<BillingConfig>();

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
                        .tenantId("TE100100")
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
                        .build();
                billingConfigList.add(billingConfig);

            for (int participantIdx = 1; participantIdx <= getRandomParticipantNumber(); participantIdx++) {
                String participantId = String.format("%04d%04d", eegIdx, participantIdx);
                String firstName = faker.name().firstName();
                String lastName = faker.name().lastName();
                boolean pIsCompany = Math.random() < 0.15;
                var pBillingMasterdata = billingMasterdata.toBuilder()
                        .participantId(UUID.randomUUID().toString())
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
                                .minusDays((long)(Math.random()*1000)+1)).build();

                for (int meteringPointIdx = 1; meteringPointIdx <= (int) (Math.random() * 4) + 1; meteringPointIdx++) {
                    String meteringPointId = String.format("%04d%04d%01d", eegIdx, participantIdx, meteringPointIdx);
                    boolean isConsumer = Math.random() < 0.66;
                    var mpBillingMasterdata = pBillingMasterdata.toBuilder()
                            .meteringPointType(isConsumer ? MeteringPointType.CONSUMER : MeteringPointType.PRODUCER)
                            .meteringPointId((isConsumer ? "VERB" : "ERZ" )+"1234000000000"+meteringPointId)
                            .equipmentNumber(meteringPointId)
                            .meteringEquipmentName(faker.company().name()
                                            + (isConsumer ? " Verbraucher" : " Anlage Süd")).build();
                    billingMasterdataList.add(mpBillingMasterdata);
                }
            }
        }

        ArrayList<BillingRun> billingRunList = new ArrayList<>();
        ArrayList<BillingDocument> billingDocumentList = new ArrayList<>();
        ArrayList<BillingDocumentItem> billingDocumentItemList = new ArrayList<>();
        long documentNumber = 1l;

        for (BillingConfig billingConfig : billingConfigList) {
            for (int i=0; i<12; i++) {
                LocalDate runStatusDate = LocalDate.now().minusMonths(13-i);
                LocalDateTime runStatusDateTime = LocalDateTime.from(runStatusDate);
                String clearingPeriodIdentifier = String.format("%s%02d%02d", billingConfig.getInvoiceNumberPrefix(),
                        runStatusDate.getYear(), runStatusDate.getMonth());
                BillingRun billingRun = BillingRun.builder()
                        .id(UUID.randomUUID())
                        .tenantId(billingConfig.getTenantId())
                        .clearingPeriodType("MONTHLY")
                        .clearingPeriodIdentifier(clearingPeriodIdentifier)
                        .runStatusDateTime(runStatusDateTime)
                        .runStatus(BillingRunStatus.DONE)
                        .build();
                billingRunList.add(billingRun);

                HashMap<UUID, List<BillingMasterdata>> participantsConsumerMeteringPoints = new HashMap<>();
                HashMap<UUID, List<BillingMasterdata>> participantsProducerMeteringPoints = new HashMap<>();

                Comparator<BillingMasterdata> participantAndMeteringPointComparator = Comparator
                        .comparing(BillingMasterdata::getParticipantId)
                        .thenComparing(BillingMasterdata::getMeteringPointId);

                billingMasterdataList.stream()
                        .filter(billingMasterdata -> billingMasterdata.getTenantId().equals(billingConfig.getTenantId()))
                        .sorted(participantAndMeteringPointComparator)
                        .forEach(billingMasterdata -> {
                            var participantId = UUID.fromString(billingMasterdata.getParticipantId());
                            var isConsumer = billingMasterdata.getMeteringPointType().equals(MeteringPointType.CONSUMER);
                            var list = isConsumer ? participantsConsumerMeteringPoints.get(participantId)
                                    : participantsProducerMeteringPoints.get(participantId);
                            if (list == null) {
                                list = new ArrayList<BillingMasterdata>();
                                if (isConsumer)
                                    participantsConsumerMeteringPoints.put(participantId, list);
                                else
                                    participantsProducerMeteringPoints.put(participantId, list);
                            }
                            list.add(billingMasterdata);
                        });

                participantsConsumerMeteringPoints.values().forEach(
                        masterdataList -> createDocumentAndItems(
                                billingDocumentList,
                                billingDocumentItemList,
                                masterdataList,
                                billingRun,
                                billingConfig,
                                BillingDocumentType.INVOICE)
                );
                participantsConsumerMeteringPoints.values().forEach(
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
                .build();
        billingDocumentList.add(billingDocument);
        masterdataList.forEach(
            masterdata -> {
                BillingDocumentItem billingDocumentItem = BillingDocumentItem.builder()
                        .id(UUID.randomUUID())
                        .tenantId(billingConfig.getTenantId())
                        .billingDocument(billingDocument)
                        .text(masterdata.getMeteringPointId())
                        .amount(BigDecimal.valueOf((long) (Math.random()*100+50)))
                        .pricePerUnit(isInvoice ? masterdata.getTariffWorkingFeePerConsumedkwh()
                                : masterdata.getTariffCreditAmountPerProducedkwh())
                        .unit("kWh")
                        .build();
                billingDocumentItemList.add(billingDocumentItem);
        });

    }

    private static void writeBillingMasterdataCsv(ArrayList<BillingMasterdata> billingMasterdataList) {

        try (PrintWriter writer = new PrintWriter(new File("billing_masterdata.csv"))) {
            StringBuilder sb = new StringBuilder();
            sb.append("id,");
            sb.append("tenant_id,");
            sb.append("eec_name,");
            sb.append("eec_id,");
            sb.append("eec_bank_iban,");
            sb.append("eec_bank_name,");
            sb.append("eec_bank_owner,");
            sb.append("eec_city,");
            sb.append("eec_zip_code,");
            sb.append("eec_street,");
            sb.append("eec_phone,");
            sb.append("eec_company_register_number,");
            sb.append("eec_tax_id,");
            sb.append("eec_vat_id,");
            sb.append("eec_subject_to_vat,");
            sb.append("tariff_type,");
            sb.append("tariff_name,");
            sb.append("tariff_text,");
            sb.append("tariff_billing_period,");
            sb.append("tariff_use_vat,");
            sb.append("tariff_vat_in_percent,");
            sb.append("tariff_participant_fee,");
            sb.append("tariff_participant_fee_name,");
            sb.append("tariff_participant_fee_text,");
            sb.append("tariff_participant_fee_use_vat,");
            sb.append("tariff_participant_fee_vat_in_percent,");
            sb.append("tariff_participant_fee_discount,");
            sb.append("tariff_basic_fee,");
            sb.append("tariff_discount,");
            sb.append("tariff_working_fee_per_consumed_kwh,");
            sb.append("tariff_credit_amount_per_produced_kwh,");
            sb.append("tariff_free_kwh,");
            sb.append("tariff_use_metering_point_fee,");
            sb.append("tariff_metering_point_fee,");
            sb.append("tariff_metering_point_fee_text,");
            sb.append("participant_id,");
            sb.append("participant_title_before,");
            sb.append("participant_first_name,");
            sb.append("participant_last_name,");
            sb.append("participant_title_after,");
            sb.append("participant_company_register_number,");
            sb.append("participant_tax_id,");
            sb.append("participant_vat_id,");
            sb.append("participant_email,");
            sb.append("participant_street,");
            sb.append("participant_zip_code,");
            sb.append("participant_city,");
            sb.append("participant_number,");
            sb.append("participant_bank_name,");
            sb.append("participant_bank_iban,");
            sb.append("participant_bank_owner,");
            sb.append("participant_sepa_mandate_reference,");
            sb.append("participant_sepa_mandate_issue_date,");
            sb.append("metering_point_type,");
            sb.append("metering_point_id,");
            sb.append("equipment_number,");
            sb.append("metering_equipment_name");
            sb.append("\n");

            for (BillingMasterdata billingMasterdata : billingMasterdataList) {
                sb.append(billingMasterdata.getId());
                sb.append(",");
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
                sb.append(billingMasterdata.getMeteringPointType());
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

            System.out.println("CSV file created successfully.");
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void writeBillingConfigCsv(ArrayList<BillingConfig> billingConfigList) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_config.csv"))) {

            writer.println("id,tenant_id,create_credit_notes_for_all_producers,before_items_text_invoice," +
                    "before_items_text_credit_note,before_items_text_info,after_items_text_invoice," +
                    "after_items_text_credit_note,after_items_text_info,terms_text_invoice," +
                    "terms_text_credit_note,terms_text_info,footer_text,document_number_sequence_length," +
                    "custom_template_file_data_id,invoice_number_prefix,invoice_number_start," +
                    "credit_note_number_prefix,credit_note_number_start");

            for (BillingConfig billingConfig : billingConfigList) {
                StringBuilder sb = new StringBuilder();
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
                        .append(billingConfig.getCustomTemplateFileDataId()).append(",")
                        .append(billingConfig.getInvoiceNumberPrefix()).append(",")
                        .append(billingConfig.getInvoiceNumberStart()).append(",")
                        .append(billingConfig.getCreditNoteNumberPrefix()).append(",")
                        .append(billingConfig.getCreditNoteNumberStart());
                writer.println(sb.toString());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void writeBillingDocumentItemCsv(ArrayList<BillingDocumentItem> billingDocumentItemList) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_document_item.csv"))) {

            //TODO
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void writeBillingDocumentCsv(ArrayList<BillingDocument> billingDocumentList) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_document.csv"))) {

            //TODO
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void writeBillingRunCsv(ArrayList<BillingRun> billingRunList) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("billing_run.csv"))) {

            //TODO
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

    }


    private static int getRandomParticipantNumber() {
        return switch ((int) (Math.random() * 10)) {
            case 1 -> (int) (Math.random() * 500) + 100;
            case 2, 3, 4 -> (int) (Math.random() * 50) + 10;
            default -> (int) (Math.random() * 10) + 5;
        };
    }

}
