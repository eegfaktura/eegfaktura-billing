package org.vfeeg.eegfaktura.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vfeeg.eegfaktura.billing.domain.*;
import org.vfeeg.eegfaktura.billing.model.*;
import org.vfeeg.eegfaktura.billing.repos.*;
import org.vfeeg.eegfaktura.billing.util.BigDecimalTools;
import org.vfeeg.eegfaktura.billing.util.StringTools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class BillingService {

    private final BillingMasterdataRepository billingMasterdataRepository;
    private final BillingDocumentNumberGenerator billingDocumentNumberGenerator;
    private final BillingRunRepository billingRunRepository;
    private final BillingDocumentItemRepository billingDocumentItemRepository;
    private final BillingDocumentRepository billingDocumentRepository;
    private final BillingConfigRepository billingConfigRepository;
    private final BillingDocumentFileRepository billingDocumentFileRepository;
    private final BillingPdfService billingPdfService;
    private final FileDataRepository fileDataRepository;
    private final ParticipantAmountService participantAmountService;

    public BillingService(final BillingPdfService billingPdfService,
                          final BillingMasterdataRepository billingMasterdataRepository,
                          final BillingDocumentNumberGenerator billingDocumentNumberGenerator,
                          final BillingRunRepository billingRunRepository,
                          final BillingDocumentRepository billingDocumentRepository,
                          final BillingDocumentItemRepository billingDocumentItemRepository,
                          final BillingConfigRepository billingConfigRepository,
                          final BillingDocumentFileRepository billingDocumentFileRepository,
                          final FileDataRepository fileDataRepository,
                          final ParticipantAmountService participantAmountService) {
        this.billingPdfService = billingPdfService;
        this.billingMasterdataRepository = billingMasterdataRepository;
        this.billingDocumentNumberGenerator = billingDocumentNumberGenerator;
        this.billingRunRepository = billingRunRepository;
        this.billingDocumentRepository = billingDocumentRepository;
        this.billingDocumentItemRepository = billingDocumentItemRepository;
        this.billingConfigRepository = billingConfigRepository;
        this.billingDocumentFileRepository = billingDocumentFileRepository;
        this.fileDataRepository = fileDataRepository;
        this.participantAmountService = participantAmountService;
    }

    @Transactional
    public DoBillingResults doBilling(DoBillingParams doBillingParams) {

        DoBillingResults doBillingResults = new DoBillingResults();
        BillingRun billingRun = null;

        try {

            // Pruefe das gewuenschte Belegdatum. Dieses darf nicht in die Zukunft
            // datiert werden => Fehler
            if (doBillingParams.getClearingDocumentDate()!=null
                    && doBillingParams.getClearingDocumentDate().isAfter(LocalDate.now())) {
                throw new RuntimeException(String.format("Ungültiges Belegdatum (%s): Rechnung darf nicht vordatiert werden.",
                        doBillingParams.getClearingDocumentDate()
                ));
            }

            // Hole Abrechnungsrelevante Daten zu EEG, Teilnehmer, Zählpunkt und Tarif
            List<BillingMasterdata> billingMasterdataList = billingMasterdataRepository
                    .findByTenantId(doBillingParams.getTenantId());

            // Hole Abrechnungseinstellungen für die EEG (tenant)
            Optional<BillingConfig> billingConfig = billingConfigRepository.findFirstByTenantId(doBillingParams.getTenantId());
            if (billingConfig.isEmpty()) {
                billingConfig = Optional.of(BillingConfigService.DEFAULT);
            }
            doBillingParams.setBillingConfig(billingConfig.get());

            // Hole oder erzeuge den Datensatz für den Abrechnungslauf (BillingRun)
            List<BillingRun> billingRunList = billingRunRepository.findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
                    doBillingParams.getTenantId(),
                    doBillingParams.getClearingPeriodType(),
                    doBillingParams.getClearingPeriodIdentifier()
            );
            // Kein passender Abrechnungslauf gefunden: Neuen erstellen!
            if (billingRunList.isEmpty()) {
                billingRun = new BillingRun();
                billingRun.setClearingPeriodIdentifier(doBillingParams.getClearingPeriodIdentifier());
                billingRun.setClearingPeriodType(doBillingParams.getClearingPeriodType());
                billingRun.setTenantId(doBillingParams.getTenantId());
                billingRun.setRunStatus(BillingRunStatus.NEW);
                billingRun.setRunStatusDateTime(LocalDateTime.now());
                billingRun = billingRunRepository.save(billingRun);
            } else {
                // Mehr als ein Abrechnungslauf gefunden? Dürfte nicht passieren!
                if (billingRunList.size() > 1) {
                    throw new IllegalStateException(String.format("Mehr als ein Abrechnungslauf gefunden?! TenantId=%s," +
                                    "ClearingPeriodType=%s, ClearingPeriodIdentifier=%s",
                            doBillingParams.getTenantId(),
                            doBillingParams.getClearingPeriodType(),
                            doBillingParams.getClearingPeriodIdentifier()
                    ));
                }
                billingRun = billingRunList.get(0);

                // Wenn der Abrechnungslauf bereits abgeschlossen oder storniert ist, dann mit Fehler beenden
                if (billingRun.getRunStatus() == BillingRunStatus.DONE
                        || billingRun.getRunStatus() == BillingRunStatus.CANCELLED) {
                    //@TODO: doBillingResult mit Inhalten der gefundenen Abrechnung befüllen
                    throw new RuntimeException(String.format("Abrechnungslauf bereits abgeschlossen" +
                                    " oder storniert! TenantId=%s," +
                                    "ClearingPeriodType=%s, ClearingPeriodIdentifier=%s",
                            doBillingParams.getTenantId(),
                            doBillingParams.getClearingPeriodType(),
                            doBillingParams.getClearingPeriodIdentifier()
                    ));
                }

                // Wenn der Abrechnungslauf noch nicht abgeschlossen ist, dann wird im Folgenden
                // eine Preview oder der finale Abrechnungslauf durchgeführt. Für diesen Fall
                // löschen wir ggf. zuvor erstellte Abrechnungsdokumente
                fileDataRepository.deleteByBillingRunId(billingRun.getId());
                billingDocumentFileRepository.deleteByBillingRunId(billingRun.getId());
                billingDocumentItemRepository.deleteByBillingRunId(billingRun.getId());
                billingDocumentRepository.deleteByBillingRunId(billingRun.getId());

            }

            // Aus den Daten des Eingangsparameters erstellen wir eine Map aus
            // Zaehlpunkten (key) mit ihren zugehoerigen Verbrauchs/Erzeugerdaten (value)
            Map<String, BigDecimal> allocationMap = Arrays.stream(doBillingParams.getAllocations())
                    .collect(Collectors.toMap(Allocation::getMeteringPoint,
                            allocation -> allocation.getAllocationKWh()!=null ? allocation.getAllocationKWh()
                            : BigDecimal.ZERO));

            // Weiters erstellen wir eine Map mit den Teilnehmern (key) und deren Zaehlpunkten (value)
            Map<String, List<BillingMasterdata>> participantsWithAllocationsMap = billingMasterdataList.stream()
                    .filter(e -> allocationMap.containsKey(e.getMeteringPointId()))
                    .collect(Collectors.groupingBy(BillingMasterdata::getParticipantId));

            // Nun wird fuer jeden Teilnehmer die Abrechnung durchgefuehrt
            for (List<BillingMasterdata> p : participantsWithAllocationsMap.values()) {
                doBillingForParticipant(p, allocationMap, doBillingParams, billingRun, doBillingResults);
            }

            if (!doBillingParams.isPreview()) {
                billingRun.setRunStatus(BillingRunStatus.DONE);
                billingRun.setRunStatusDateTime(LocalDateTime.now());
            }

            billingRun = billingRunRepository.save(billingRun);

            doBillingResults.setBillingRunId(billingRun.getId());
            doBillingResults.setAbstractText("Abrechnung " + (doBillingParams.isPreview() ? "(Vorschau)" : "")
                    + ": erfolgreich abgeschlossen.");
        } catch (Exception e) {
            log.error("Abrechnung fehlgeschlagen", e);
            doBillingResults.setAbstractText("Abrechnung fehlgeschlagen: "+e.getMessage());
            doBillingResults.setBillingRunId(billingRun!=null ? billingRun.getId() : null);
        }
        return doBillingResults;
    }

    private void doBillingForParticipant(List<BillingMasterdata> billingMasterdataList,
                                         Map<String, BigDecimal> allocationMap,
                                         DoBillingParams doBillingParams, BillingRun billingRun,
                                         DoBillingResults doBillingResults) {

        BillingMasterdata firstBillingMasterdata = billingMasterdataList.get(0);
        ParticipantAmount participantAmount = new ParticipantAmount();
        participantAmount.setId(UUID.fromString(firstBillingMasterdata.getParticipantId()));

        // (1) Erzeuge Rechnungen (INVOICES)
        {
            List<BillingMasterdata> consumersOnly = billingMasterdataList.stream().filter(
                    billingMasterdata -> billingMasterdata.getMeteringPointType().equals(MeteringPointType.CONSUMER)).toList();

            final BillingDocument invoice = createBillingDocument(firstBillingMasterdata,
                    BillingDocumentType.INVOICE,
                    doBillingParams);
            List<BillingDocumentItem> invoiceItems = new ArrayList<>();
            consumersOnly.forEach(e -> createBillingDocumentItem(invoice, invoiceItems, e, allocationMap));

            // Wenn Mitgliedsbeitrag (tariffParticipantFee) not null, dann als Position hinzufügen
            createCustomBillingDocumentItem(
                    invoice,
                    invoiceItems,
                    StringUtils.defaultIfBlank(firstBillingMasterdata.getTariffParticipantFeeName(),
                            "Mitgliedsbeitrag"),
                    firstBillingMasterdata.getTariffParticipantFee(),
                    firstBillingMasterdata.getTariffParticipantFeeDiscount(),
                    firstBillingMasterdata.getTariffParticipantFeeUseVat(),
                    firstBillingMasterdata.getTariffParticipantFeeVatInPercent()
            );

            // Zählpunktgebühren werden erstellt
            billingMasterdataList.stream().filter(
                    BillingMasterdata::getTariffUseMeteringPointFee
            ).forEach(m -> createMeteringPointFeeDocumentItem(invoice, invoiceItems, m));


            // Wenn Grundgebühr (tariffBasicFee) not null, dann als Position hinzufügen
//            createCustomBillingDocumentItem(
//                    invoice,
//                    invoiceItems,
//                    "Grundgebühr",
//                    firstBillingMasterdata.getTariffBasicFee(),
//                    firstBillingMasterdata.getTariffParticipantFeeDiscount(),
//                    firstBillingMasterdata.getTariffParticipantFeeUseVat(),
//                    firstBillingMasterdata.getTariffParticipantFeeVatInPercent()
//            );

            calculateGrossValues(invoice, invoiceItems);

            // Wir erstellen nur Rechnungen mit Beträgen > 0 und keine Nullrechnungen
            if (!BigDecimalTools.isNullOrZero(invoice.getGrossAmountInEuro())) {
                createAndAddDocumentNumber(invoice, firstBillingMasterdata, doBillingParams);
                billingPdfService.createAndSavePDF(invoice, invoiceItems,
                        doBillingParams.getBillingConfig().getHeaderImageFileDataId(),
                        doBillingParams.getBillingConfig().getFooterImageFileDataId(),
                        doBillingParams.isPreview());
                invoice.setBillingRun(billingRun);
                billingDocumentRepository.save(invoice);
            }
            participantAmountService.addParticipantAmountData(participantAmount, invoice, invoiceItems);
        }

        // (2) Erzeuge Gutschriften (CREDIT_NOTES) bzw. ggf. Info-Dokument (INFO) für alle Erzeuger
        {
            List<BillingMasterdata> producersOnly = billingMasterdataList.stream().filter(
                    e -> e.getMeteringPointType().equals(MeteringPointType.PRODUCER)).toList();

            // Wenn der Erzeuger keine UID hat,
            //     dann bekommt er eine normale Gutschrift (ohne UST) mit Fixtexten zu Gutschriften
            // Wenn der Erzeuger eine UID hat UND die EEG nur Erzeugern ohne UID eine Gutschrift schickt
            //     dann wird ein Info Dokument erstellt mit UST und den Fixtexten
            //     gem. BillingConfig INFO Dokumenten
            // Wenn der Erzeuger eine UID hat UND die EEG allen Erzeugern Gutschriften schickt,
            //     dann wird eine Reverse Charge Gutschrift erstellt mit UST UND den
            //     Fixtexten gem. BillingConfig INFO Dokumenten
            final BillingDocument producerDocument = createBillingDocument(billingMasterdataList.get(0),
                    StringUtils.isNotEmpty(billingMasterdataList.get(0).getParticipantVatId()) ?
                            (doBillingParams.getBillingConfig().isCreateCreditNotesForAllProducers() ?
                                    BillingDocumentType.CREDIT_NOTE_RC : BillingDocumentType.INFO)
                            : BillingDocumentType.CREDIT_NOTE,
                    doBillingParams);
            List<BillingDocumentItem> creditNotesItems = new ArrayList<>();
            producersOnly.forEach(e -> createBillingDocumentItem(producerDocument, creditNotesItems, e, allocationMap));

            calculateGrossValues(producerDocument, creditNotesItems);

            if (!BigDecimalTools.isNullOrZero(producerDocument.getGrossAmountInEuro())) {
                createAndAddDocumentNumber(producerDocument, firstBillingMasterdata, doBillingParams);
                billingPdfService.createAndSavePDF(producerDocument, creditNotesItems,
                        doBillingParams.getBillingConfig().getHeaderImageFileDataId(),
                        doBillingParams.getBillingConfig().getFooterImageFileDataId(), doBillingParams.isPreview());
                producerDocument.setBillingRun(billingRun);
                billingDocumentRepository.save(producerDocument);
            }
            participantAmountService.addParticipantAmountData(participantAmount, producerDocument,
                    creditNotesItems);
        }

        doBillingResults.getParticipantAmounts().add(participantAmount);
    }

    private BillingDocument createBillingDocument(BillingMasterdata billingMasterdata,
                                                  BillingDocumentType billingDocumentType,
                                                  DoBillingParams doBillingParams) {

        LocalDate documentDate = doBillingParams.getClearingDocumentDate();
        documentDate = documentDate == null ? LocalDate.now() : documentDate;

        BillingDocument billingDocument = new BillingDocument();
        billingDocument.setTenantId(billingMasterdata.getTenantId());
        billingDocument.setBillingDocumentType(billingDocumentType);

        billingDocument.setDocumentDate(documentDate);
        billingDocument.setClearingPeriodType(doBillingParams.getClearingPeriodType());
        billingDocument.setClearingPeriodIdentifier(doBillingParams.getClearingPeriodIdentifier());

        billingDocument.setBeforeItemsText(switch(billingDocumentType){
            case INVOICE -> doBillingParams.getBillingConfig().getBeforeItemsTextInvoice();
            case CREDIT_NOTE -> doBillingParams.getBillingConfig().getBeforeItemsTextCreditNote();
            case CREDIT_NOTE_RC, INFO -> doBillingParams.getBillingConfig().getBeforeItemsTextInfo();
        });
        billingDocument.setAfterItemsText(switch(billingDocumentType){
            case INVOICE -> doBillingParams.getBillingConfig().getAfterItemsTextInvoice();
            case CREDIT_NOTE -> doBillingParams.getBillingConfig().getAfterItemsTextCreditNote();
            case CREDIT_NOTE_RC, INFO -> doBillingParams.getBillingConfig().getAfterItemsTextInfo();
        });
        billingDocument.setTermsText(switch(billingDocumentType){
            case INVOICE -> doBillingParams.getBillingConfig().getTermsTextInvoice();
            case CREDIT_NOTE -> doBillingParams.getBillingConfig().getTermsTextCreditNote();
            case CREDIT_NOTE_RC, INFO -> doBillingParams.getBillingConfig().getTermsTextInfo();
        });

        final String footerText = doBillingParams.getBillingConfig().getFooterText();
        billingDocument.setFooterText(StringUtils.isNotEmpty(footerText)
            ? footerText.replace("##", "\n") : footerText);

        final String beforeItemsText = billingDocument.getBeforeItemsText();
        billingDocument.setBeforeItemsText(StringUtils.isNotEmpty(beforeItemsText) ? beforeItemsText.replace("##", "\n"): beforeItemsText);

        final String afterItemsText = billingDocument.getAfterItemsText();
        billingDocument.setAfterItemsText(StringUtils.isNotEmpty(afterItemsText) ? afterItemsText.replace("##", "\n") : afterItemsText);

        final String itemsText = billingDocument.getTermsText();
        billingDocument.setTermsText(StringUtils.isNotEmpty(itemsText) ? itemsText.replace("##", "\n") : itemsText);

        billingDocument.setIssuerName(billingMasterdata.getEecName());
        billingDocument.setIssuerAddressLine1(billingMasterdata.getEecStreet());
        billingDocument.setIssuerAddressLine2(StringTools.nullSafeJoin(" ",
                billingMasterdata.getEecZipCode(), billingMasterdata.getEecCity()));
        billingDocument.setIssuerTaxId(billingMasterdata.getEecTaxId());
        billingDocument.setIssuerVatId(billingMasterdata.getEecVatId());
        billingDocument.setIssuerCompanyRegisterNumber(billingMasterdata.getEecCompanyRegisterNumber());
        billingDocument.setIssuerMail(billingMasterdata.getEecEmail());
        billingDocument.setIssuerPhone(billingMasterdata.getEecPhone());
        billingDocument.setIssuerBankName(billingMasterdata.getEecBankName());
        billingDocument.setIssuerBankIBAN(billingMasterdata.getEecBankIban());
        billingDocument.setIssuerBankOwner(billingMasterdata.getEecBankOwner());
        billingDocument.setParticipantId(billingMasterdata.getParticipantId());
        billingDocument.setRecipientName(StringTools.nullSafeJoin(" ",
                        billingMasterdata.getParticipantTitleBefore(),
                        billingMasterdata.getParticipantFirstname(),
                        billingMasterdata.getParticipantLastname(),
                        billingMasterdata.getParticipantTitleAfter())
        );
        billingDocument.setRecipientParticipantNumber(billingMasterdata.getParticipantNumber());
        billingDocument.setRecipientBankName(billingMasterdata.getParticipantBankName());
        billingDocument.setRecipientBankIban(billingMasterdata.getParticipantBankIban());
        billingDocument.setRecipientBankOwner(billingMasterdata.getParticipantBankOwner());
        billingDocument.setRecipientSepaMandateReference(billingMasterdata.getParticipantSepaMandateReference());
        billingDocument.setRecipientSepaMandateIssueDate(billingMasterdata.getParticipantSepaMandateIssueDate());
        billingDocument.setRecipientEmail(billingMasterdata.getParticipantEmail());
        billingDocument.setRecipientAddressLine1(billingMasterdata.getParticipantStreet());
        billingDocument.setRecipientAddressLine2(StringTools.nullSafeJoin(" ",
                billingMasterdata.getParticipantZipCode(), billingMasterdata.getParticipantCity()));

        return billingDocumentRepository.save(billingDocument);
    }

    private void createAndAddDocumentNumber(BillingDocument billingDocument,
            BillingMasterdata billingMasterdata,
            DoBillingParams doBillingParams) {

        BillingDocumentType billingDocumentType = billingDocument.getBillingDocumentType();

        if (billingDocumentType == null) {
            throw new IllegalArgumentException("BillingDocumentType is null");
        }

        if (billingDocumentType == BillingDocumentType.INFO) {
            billingDocument.setDocumentNumber("-");
        } else {
            BillingDocumentNumber documentNumber = !doBillingParams.isPreview()  ?
                    billingDocumentNumberGenerator.getNext(
                            billingMasterdata.getTenantId(),
                            billingDocument.getDocumentDate().getYear(),
                            billingDocumentType==BillingDocumentType.INVOICE ?
                                    doBillingParams.getBillingConfig().getInvoiceNumberPrefix() :
                                    doBillingParams.getBillingConfig().getCreditNoteNumberPrefix(),
                            billingDocumentType==BillingDocumentType.INVOICE ?
                                    doBillingParams.getBillingConfig().getInvoiceNumberStart() :
                                    doBillingParams.getBillingConfig().getCreditNoteNumberStart(),
                            doBillingParams.getBillingConfig().getDocumentNumberSequenceLength()
                    ) : new BillingDocumentNumber();

            billingDocument.setDocumentNumber(documentNumber.getDocumentNumber());
        }


    }

    private String buildItemText(BillingMasterdata billingMasterdata) {
        return StringTools.nullSafeJoin("\n",
                StringUtils.isNotEmpty(billingMasterdata.getMeteringEquipmentName())
                        ? "Anlage-Name: "+billingMasterdata.getMeteringEquipmentName() : null,
                StringUtils.isNotEmpty(billingMasterdata.getEquipmentNumber())
                        ? "Anlage-Nr.: "+billingMasterdata.getEquipmentNumber() : null,
                billingMasterdata.getMeteringPointId()
        );
    }

    private void createBillingDocumentItem(BillingDocument billingDocument,
                                           List<BillingDocumentItem> billingDocumentItems,
                                           BillingMasterdata billingMasterdata,
                                           Map<String, BigDecimal> allocationMap) {

        if (billingMasterdata.getMeteringPointType()!=MeteringPointType.CONSUMER &&
                billingMasterdata.getMeteringPointType()!=MeteringPointType.PRODUCER) {
            throw new IllegalArgumentException("Unknown or empty MeteringPointType");
        }

        BillingDocumentItem newBillingDocumentItem = new BillingDocumentItem();
        newBillingDocumentItem.setMeteringPointId(billingMasterdata.getMeteringPointId());
        newBillingDocumentItem.setMeteringPointType(billingMasterdata.getMeteringPointType());
        newBillingDocumentItem.setText(buildItemText(billingMasterdata));

        BigDecimal amount = BigDecimalTools.makeZeroIfNull(allocationMap.get(billingMasterdata.getMeteringPointId()));
        BigDecimal tariffFreekwh = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffFreekwh());

        // Freie kWh berücksichtigen
        if (billingMasterdata.getMeteringPointType()==MeteringPointType.CONSUMER &&
                !BigDecimalTools.isNullOrZero(tariffFreekwh)) {
                String newText = newBillingDocumentItem.getText();
                BigDecimal newAmount = amount.subtract(tariffFreekwh);
                newAmount = newAmount.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO : newAmount;
                newText += String.format(
                        "\nMenge (%s) = Verbrauch (%s) abzgl. freie kWh (%s)",
                        BigDecimalTools.makeGermanString(newAmount, "kWh"),
                        BigDecimalTools.makeGermanString(amount, "kWh"),
                        BigDecimalTools.makeGermanString(tariffFreekwh, "kWh")
                );
                newBillingDocumentItem.setText(newText);
                amount = newAmount;
        }

        BigDecimal tariffPpuInCent =
                billingMasterdata.getMeteringPointType()==MeteringPointType.CONSUMER ?
                BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffWorkingFeePerConsumedkwh()) :
                BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffCreditAmountPerProducedkwh());

        BigDecimal discountPercent = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffDiscount());
        BigDecimal netValue = amount.multiply(tariffPpuInCent).divide(BigDecimal.valueOf(100.0));
        BigDecimal discountValue = netValue.multiply(discountPercent.divide(BigDecimal.valueOf(100.0)));
        netValue = netValue.subtract(discountValue);
        BigDecimal vatPercent = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffVatInPercent());
        BigDecimal vatEuro = BigDecimal.valueOf(0);
        if (billingMasterdata.getTariffUseVat()) {
            vatEuro = netValue.multiply(vatPercent.divide(BigDecimal.valueOf(100.0)));
        }
        BigDecimal grossValue = netValue.add(vatEuro);

        if (BigDecimalTools.isNullOrZero(grossValue)) return; // Keine Nullposition!

        newBillingDocumentItem.setAmount(amount);
        newBillingDocumentItem.setPricePerUnit(tariffPpuInCent);
        newBillingDocumentItem.setDiscountPercent(discountPercent);
        newBillingDocumentItem.setVatPercent(vatPercent);
        newBillingDocumentItem.setNetValue(netValue);
        newBillingDocumentItem.setVatValueInEuro(vatEuro);
        newBillingDocumentItem.setGrossValue(grossValue);
        newBillingDocumentItem.setBillingDocument(billingDocument);

        billingDocumentItems.add(
                billingDocumentItemRepository.save(newBillingDocumentItem));

    }

    /**
     * NOTE that custom billing document items do not support amount nor discount! Also ppuUnit is always €.
     * @param billingDocument
     * @param billingDocumentItems
     * @param text
     * @param pricePerUnit
     * @param useVat
     * @param vatPercent
     */
    private void createCustomBillingDocumentItem(BillingDocument billingDocument,
                                           List<BillingDocumentItem> billingDocumentItems,
                                           String text,
                                           BigDecimal pricePerUnit,
                                           BigDecimal discountPercent,
                                           boolean useVat,
                                           BigDecimal vatPercent) {

        if (BigDecimalTools.isNullOrZero(pricePerUnit)) return;

        BillingDocumentItem newBillingDocumentItem = new BillingDocumentItem();

        BigDecimal discountPercentSafe = BigDecimalTools.makeZeroIfNull(discountPercent);
        BigDecimal discountValue = pricePerUnit.multiply(discountPercentSafe.divide(BigDecimal.valueOf(100.0)));
        BigDecimal netValue = pricePerUnit.subtract(discountValue);
        BigDecimal vatPercentSafe = BigDecimalTools.makeZeroIfNull(vatPercent);
        BigDecimal vatEuro = useVat ? netValue
                .multiply(vatPercentSafe.divide(BigDecimal.valueOf(100.0)))
                    : BigDecimal.valueOf(0);
        BigDecimal grossValue = netValue.add(vatEuro);

        if (BigDecimalTools.isNullOrZero(grossValue)) return; // Keine Nullposition!

        newBillingDocumentItem.setText(text);
        newBillingDocumentItem.setAmount(BigDecimal.ONE);
        newBillingDocumentItem.setPricePerUnit(pricePerUnit);
        newBillingDocumentItem.setPpuUnit("€");
        newBillingDocumentItem.setDiscountPercent(discountPercentSafe);
        newBillingDocumentItem.setNetValue(netValue);
        newBillingDocumentItem.setVatPercent(vatPercent);
        newBillingDocumentItem.setVatValueInEuro(vatEuro);
        newBillingDocumentItem.setGrossValue(grossValue);
        newBillingDocumentItem.setBillingDocument(billingDocument);

        billingDocumentItems.add(
                billingDocumentItemRepository.save(newBillingDocumentItem));

    }

    /**
     * Creates Meteringpoint fee items for customer invoices.
     * Regardless of the energy direction, all meteringpoint fees are shown in the invoice.
     *
     * @param billingDocument
     * @param billingDocumentItems
     * @param billingMasterdata
     */
    private void createMeteringPointFeeDocumentItem(BillingDocument billingDocument,
                                                    List<BillingDocumentItem> billingDocumentItems,
                                                    BillingMasterdata billingMasterdata) {
        BigDecimal pricePerMeter = billingMasterdata.getTariffMeteringPointFee();
        if (BigDecimalTools.isNullOrZero(pricePerMeter)) return;

        boolean useVat = billingMasterdata.getTariffUseVat();
        BigDecimal vatPercent = billingMasterdata.getTariffVatInPercent();

        BillingDocumentItem newBillingDocumentItem = new BillingDocumentItem();

        BigDecimal vatPercentSafe = BigDecimalTools.makeZeroIfNull(vatPercent);
        BigDecimal vatEuro = useVat ? pricePerMeter
                .multiply(vatPercentSafe.divide(BigDecimal.valueOf(100.0)))
                : BigDecimal.valueOf(0);
        BigDecimal grossValue = pricePerMeter.add(vatEuro);

        if (BigDecimalTools.isNullOrZero(grossValue)) return; // Keine Nullposition!

        newBillingDocumentItem.setText(String.format("Zählpunktgebühr: %s", billingMasterdata.getMeteringPointId()));
        newBillingDocumentItem.setAmount(BigDecimal.ONE);
        newBillingDocumentItem.setPricePerUnit(pricePerMeter);
        newBillingDocumentItem.setDiscountPercent(BigDecimal.ZERO);
        newBillingDocumentItem.setPpuUnit("€");
        newBillingDocumentItem.setNetValue(pricePerMeter);
        newBillingDocumentItem.setVatPercent(vatPercent);
        newBillingDocumentItem.setVatValueInEuro(vatEuro);
        newBillingDocumentItem.setGrossValue(grossValue);
        newBillingDocumentItem.setBillingDocument(billingDocument);

        billingDocumentItems.add(
                billingDocumentItemRepository.save(newBillingDocumentItem));

    }

    // Beträge der Position (item) zu Gesamtbeträgen aufsummieren
    private void calculateGrossValues(BillingDocument billingDocument,
                                      List<BillingDocumentItem> billingDocumentItems) {

        billingDocumentItems.forEach( billingDocumentItem -> {

            // Werte aus aktueller Position (Item) auslesen
            final BigDecimal vatPercent = billingDocumentItem.getVatPercent();
            final BigDecimal vatEuro = billingDocumentItem.getVatValueInEuro();

            // Wir unterstützen bis zu 2 unterschiedliche UST Sätze
            if (vatPercent.compareTo(BigDecimal.valueOf(0))!=0) {

                // In Dokument bislang aufgelaufene UST Sätze (bis zu 2) und deren Summe auslesen
                BigDecimal invoiceVat1Percent = BigDecimalTools.makeZeroIfNull(billingDocument.getVat1Percent());
                BigDecimal invoiceVat1SumInEuro = BigDecimalTools.makeZeroIfNull(billingDocument.getVat1SumInEuro());
                BigDecimal invoiceVat2Percent = BigDecimalTools.makeZeroIfNull(billingDocument.getVat2Percent());
                BigDecimal invoiceVat2SumInEuro = BigDecimalTools.makeZeroIfNull(billingDocument.getVat2SumInEuro());
                // Neuer UST Satz bereits in erstem oder zweiten UST Summenbetrag berücksichtigt?
                if (invoiceVat1Percent.compareTo(BigDecimal.valueOf(0))==0) {
                    invoiceVat1Percent = vatPercent;
                    billingDocument.setVat1Percent(vatPercent);
                } else if (!invoiceVat1Percent.equals(vatPercent) &&
                        invoiceVat2Percent.compareTo(BigDecimal.valueOf(0))==0) {
                    invoiceVat2Percent = vatPercent;
                    billingDocument.setVat2Percent(vatPercent);
                }
                // Neuen UST Betrag im passenden UST Satz aufsummieren
                if (invoiceVat1Percent.equals(vatPercent)) {
                    billingDocument.setVat1SumInEuro(invoiceVat1SumInEuro.add(vatEuro));
                } else if (vatPercent.equals(invoiceVat2Percent)) {
                    billingDocument.setVat2SumInEuro(invoiceVat2SumInEuro.add(vatEuro));
                } else {
                    throw new IllegalArgumentException("More than 2 VAT rates not supported. Rate 1=%f, Rate 2=%f, New=%f"
                            .formatted(invoiceVat1Percent, invoiceVat2Percent, vatPercent));
                }
            }
            BigDecimal netAmountInEuro = BigDecimalTools.makeZeroIfNull(billingDocument.getNetAmountInEuro());
            billingDocument.setNetAmountInEuro(netAmountInEuro.add(billingDocumentItem.getNetValue()));
            BigDecimal invoiceGrossAmountInEuro = BigDecimalTools.makeZeroIfNull(billingDocument.getGrossAmountInEuro());
            billingDocument.setGrossAmountInEuro(invoiceGrossAmountInEuro.add(billingDocumentItem.getGrossValue()));
        });

    }


}
