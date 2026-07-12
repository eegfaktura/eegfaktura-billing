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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class BillingService {

    public static final String ZAEHLPUNKTGEBUEHR_TEXT = "Zählpunktgebühr";
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

    /**
     * Annahme-Teil des Abrechnungslaufs (laeuft im Request-Thread): validiert
     * das Belegdatum, laedt oder erzeugt den BillingRun und prueft den
     * DONE/CANCELLED-Guard. Wirft bei Ablehnung - der Lauf selbst wird hier
     * noch NICHT gerechnet (siehe executeBillingRun).
     */
    @Transactional
    public BillingRun acceptBillingRun(DoBillingParams doBillingParams) {

        // Pruefe das gewuenschte Belegdatum. Dieses darf nicht in die Zukunft
        // datiert werden => Fehler
        if (doBillingParams.getClearingDocumentDate()!=null
                && doBillingParams.getClearingDocumentDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(String.format("Ungültiges Belegdatum (%s): Rechnung darf nicht vordatiert werden.",
                    doBillingParams.getClearingDocumentDate()
            ));
        }

        // Hole oder erzeuge den Datensatz für den Abrechnungslauf (BillingRun)
        List<BillingRun> billingRunList = billingRunRepository.findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
                doBillingParams.getTenantId(),
                doBillingParams.getClearingPeriodType(),
                doBillingParams.getClearingPeriodIdentifier()
        );

        // Kein passender Abrechnungslauf gefunden: Neuen erstellen!
        BillingRun billingRun;
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
            // (Seit V1_15 zusaetzlich durch Unique-Index abgesichert.)
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
                throw new BillingRunAlreadyClosedException(String.format("Abrechnungslauf bereits abgeschlossen" +
                                " oder storniert! TenantId=%s," +
                                "ClearingPeriodType=%s, ClearingPeriodIdentifier=%s",
                        doBillingParams.getTenantId(),
                        doBillingParams.getClearingPeriodType(),
                        doBillingParams.getClearingPeriodIdentifier()
                ));
            }
        }
        return billingRun;
    }

    /** Bereits abgeschlossener/stornierter Lauf - vom Aufrufer als Konflikt zu behandeln. */
    public static class BillingRunAlreadyClosedException extends RuntimeException {
        public BillingRunAlreadyClosedException(String message) { super(message); }
    }

    /**
     * Atomarer Status-Claim (RUNNING nur aus NEW/FAILED) - genau ein Gewinner,
     * auch bei konkurrierenden Starts ueber mehrere Replicas.
     */
    @Transactional
    public boolean claimBillingRun(UUID billingRunId) {
        return billingRunRepository.claimRun(billingRunId, LocalDateTime.now()) > 0;
    }

    /**
     * Rollback eines Claims (z.B. Executor voll): RUNNING -> vorheriger Status.
     */
    @Transactional
    public void revertBillingRunClaim(UUID billingRunId, BillingRunStatus previousStatus) {
        if (billingRunRepository.releaseClaim(billingRunId, previousStatus, null, LocalDateTime.now()) == 0) {
            log.warn("Claim-Rollback ohne Wirkung (Run {} nicht mehr RUNNING)", billingRunId);
        }
    }

    /**
     * Markiert einen laufenden Abrechnungslauf als FAILED und persistiert eine
     * kurze fachliche Fehlerzusammenfassung (keine Stacktraces).
     */
    @Transactional
    public void markBillingRunFailed(UUID billingRunId, String errorSummary) {
        if (billingRunRepository.releaseClaim(billingRunId, BillingRunStatus.FAILED, errorSummary, LocalDateTime.now()) == 0) {
            log.warn("FAILED-Markierung ohne Wirkung (Run {} nicht mehr RUNNING)", billingRunId);
        }
    }

    /**
     * Synchrone Komposition aus Annahme + Berechnung - fuer Tests und
     * interne Aufrufer. Der asynchrone Pfad laeuft ueber acceptBillingRun /
     * claimBillingRun / BillingRunLauncher.
     */
    @Transactional
    public DoBillingResults doBilling(DoBillingParams doBillingParams) {
        BillingRun billingRun = acceptBillingRun(doBillingParams);
        return executeBillingRun(billingRun.getId(), doBillingParams);
    }

    /**
     * Rechen-Teil des Abrechnungslaufs (laeuft asynchron im Launcher bzw.
     * synchron via doBilling). Exceptions propagieren - der fruehere
     * catch-all lebt jetzt im BillingRunLauncher (-> Status FAILED).
     */
    @Transactional
    public DoBillingResults executeBillingRun(UUID billingRunId, DoBillingParams doBillingParams) {

        DoBillingResults doBillingResults = new DoBillingResults();

        BillingRun billingRun = billingRunRepository.findById(billingRunId)
                .orElseThrow(() -> new IllegalStateException("Abrechnungslauf nicht gefunden: " + billingRunId));

        {
            // Hole Abrechnungsrelevante Daten zu EEG, Teilnehmer, Zählpunkt und Tarif
            List<BillingMasterdata> billingMasterdataList = billingMasterdataRepository
                    .findByTenantId(doBillingParams.getTenantId());

            // Hole Abrechnungseinstellungen für die EEG (tenant)
            Optional<BillingConfig> billingConfig = billingConfigRepository.findFirstByTenantId(doBillingParams.getTenantId());
            if (billingConfig.isEmpty()) {
                billingConfig = Optional.of(BillingConfigService.DEFAULT);
            }
            doBillingParams.setBillingConfig(billingConfig.get());

            // Vor der (Neu-)Berechnung loeschen wir ggf. zuvor erstellte
            // Abrechnungsdokumente (Preview-Wiederholung oder FAILED-Neustart)
            fileDataRepository.deleteByBillingRunId(billingRun.getId());
            billingDocumentFileRepository.deleteByBillingRunId(billingRun.getId());
            billingDocumentItemRepository.deleteByBillingRunId(billingRun.getId());
            billingDocumentRepository.deleteByBillingRunId(billingRun.getId());

            // Aus den Daten des Eingangsparameters erstellen wir eine Map aus dem Tupel
            // Teilnehmer/Zaehlpunkt (key) mit ihren zugehoerigen Verbrauchs/Erzeugerdaten (value).
            // Seit ZVT traegt die Allocation neben allocationKWh optional buckets/timeWindows.
            Map<String, Allocation> allocationMap = Arrays.stream(doBillingParams.getAllocations())
                    .collect(Collectors.toMap(
                            allocation -> allocation.getParticipantId()+"@"+allocation.getMeteringPoint(),
                            allocation -> allocation));

            // Weiters erstellen wir eine Map mit den Teilnehmern (key) und deren Zaehlpunkten (value)
            Map<String, List<BillingMasterdata>> participantsWithAllocationsMap = billingMasterdataList.stream()
                    .filter(e -> allocationMap.containsKey(e.getParticipantId()+"@"+e.getMeteringPointId()))
                    .collect(Collectors.groupingBy(BillingMasterdata::getParticipantId));

            // Nun wird fuer jeden Teilnehmer die Abrechnung durchgefuehrt
            for (List<BillingMasterdata> p : participantsWithAllocationsMap.values()) {
                doBillingForParticipant(p, allocationMap, doBillingParams, billingRun, doBillingResults);
            }

            // Preview-Laeufe bleiben wiederholbar (NEW), finale Laeufe sind DONE.
            // Explizit setzen - im Async-Pfad steht der Lauf hier auf RUNNING.
            billingRun.setRunStatus(doBillingParams.isPreview() ? BillingRunStatus.NEW : BillingRunStatus.DONE);
            billingRun.setRunStatusDateTime(LocalDateTime.now());

            billingRun = billingRunRepository.save(billingRun);

            doBillingResults.setBillingRunId(billingRun.getId());
            doBillingResults.setAbstractText("Abrechnung " + (doBillingParams.isPreview() ? "(Vorschau)" : "")
                    + ": erfolgreich abgeschlossen.");
        }
        return doBillingResults;
    }

    private void doBillingForParticipant(List<BillingMasterdata> billingMasterdataList,
                                         Map<String, Allocation> allocationMap,
                                         DoBillingParams doBillingParams, BillingRun billingRun,
                                         DoBillingResults doBillingResults) {

        BillingMasterdata firstBillingMasterdata = billingMasterdataList.get(0);
        ParticipantAmount participantAmount = new ParticipantAmount();
        participantAmount.setId(UUID.fromString(firstBillingMasterdata.getParticipantId()));

        // ZP-Bezeichnungen fuer die Block-Kopfzeilen im PDF (je Zaehlpunkt ein Block)
        Map<String, String> meteringPointNames = billingMasterdataList.stream()
                .collect(Collectors.toMap(BillingMasterdata::getMeteringPointId,
                        m -> StringUtils.defaultString(m.getMeteringEquipmentName()),
                        (a, b) -> a));

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
            final String documentText = firstBillingMasterdata.getTariffParticipantFeeText();
            createCustomBillingDocumentItem(
                    invoice,
                    invoiceItems,
                    StringUtils.defaultIfBlank(firstBillingMasterdata.getTariffParticipantFeeName(),
                            "Mitgliedsbeitrag"),
                    StringUtils.isNotEmpty(documentText)
                            ? documentText.replace("##", "\n")
                            : documentText,
                    firstBillingMasterdata.getTariffParticipantFeeName(),
                    firstBillingMasterdata.getTariffParticipantFee(),
                    firstBillingMasterdata.getTariffParticipantFeeDiscount(),
                    firstBillingMasterdata.getTariffParticipantFeeUseVat(),
                    firstBillingMasterdata.getTariffParticipantFeeVatInPercent()
            );

            // Zählpunktgebühren werden erstellt
            billingMasterdataList.stream().filter(
                    BillingMasterdata::getTariffUseMeteringPointFee
            ).forEach(m -> createMeteringPointFeeDocumentItem(invoice, invoiceItems, m));

            calculateGrossValues(invoice, invoiceItems);

            // Wir erstellen nur Rechnungen mit Beträgen > 0 und keine Nullrechnungen
            if (!BigDecimalTools.isNullOrZero(invoice.getGrossAmountInEuro())) {
                createAndAddDocumentNumber(invoice, firstBillingMasterdata, doBillingParams);
                billingPdfService.createAndSavePDF(invoice, invoiceItems, meteringPointNames,
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
                billingPdfService.createAndSavePDF(producerDocument, creditNotesItems, meteringPointNames,
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
        billingDocument.setIssuerBankCreditorId(billingMasterdata.getEecBankCreditorId());
        billingDocument.setParticipantId(billingMasterdata.getParticipantId());
        billingDocument.setRecipientName(StringTools.nullSafeJoin(" ",
                        billingMasterdata.getParticipantTitleBefore(),
                        billingMasterdata.getParticipantFirstname(),
                        billingMasterdata.getParticipantLastname(),
                        billingMasterdata.getParticipantTitleAfter())
        );
        billingDocument.setRecipientFirstname(billingMasterdata.getParticipantFirstname());
        billingDocument.setRecipientLastname(billingMasterdata.getParticipantLastname());
        billingDocument.setRecipientParticipantNumber(billingMasterdata.getParticipantNumber());
        billingDocument.setRecipientBankName(billingMasterdata.getParticipantBankName());
        billingDocument.setRecipientBankIban(billingMasterdata.getParticipantBankIban());
        billingDocument.setRecipientBankOwner(billingMasterdata.getParticipantBankOwner());
        billingDocument.setRecipientSepaMandateReference(billingMasterdata.getParticipantSepaMandateReference());
        billingDocument.setRecipientSepaMandateIssueDate(billingMasterdata.getParticipantSepaMandateIssueDate());
        billingDocument.setRecipientSepaDirectDebit(billingMasterdata.getParticipantSepaDirectDebit());
        billingDocument.setRecipientEmail(billingMasterdata.getParticipantEmail());
        billingDocument.setRecipientTaxId(billingMasterdata.getParticipantTaxId());
        billingDocument.setRecipientVatId(billingMasterdata.getParticipantVatId());
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
                                           Map<String, Allocation> allocationMap) {

        if (billingMasterdata.getMeteringPointType()!=MeteringPointType.CONSUMER &&
                billingMasterdata.getMeteringPointType()!=MeteringPointType.PRODUCER) {
            throw new IllegalArgumentException("Unknown or empty MeteringPointType");
        }

        Allocation allocation = allocationMap.get(
                billingMasterdata.getParticipantId()+"@"+billingMasterdata.getMeteringPointId());
        boolean useTimeTariff = Boolean.TRUE.equals(billingMasterdata.getTariffUseTimeTariff());

        if (!useTimeTariff) {
            // Einfach-Tarif: fail-loud, wenn der Aufrufer Fenster-Teilsummen
            // mitschickt - Preis und Menge wuerden sonst still auseinanderlaufen.
            if (allocation != null && allocation.getBuckets() != null && allocation.getBuckets().length > 0) {
                throw new ZvtContractViolationException(String.format(
                        "Zaehlpunkt %s: buckets im Payload, aber der Tarif ist nicht zeitbasiert. " +
                        "Tarifkonfiguration und Abrechnungsaufruf passen nicht zusammen.",
                        billingMasterdata.getMeteringPointId()));
            }

            BigDecimal amount = BigDecimalTools.makeZeroIfNull(
                    allocation != null ? allocation.getAllocationKWh() : null)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal tariffPpuInCent =
                    billingMasterdata.getMeteringPointType()==MeteringPointType.CONSUMER ?
                    BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffWorkingFeePerConsumedkwh()) :
                    BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffCreditAmountPerProducedkwh());

            createEnergyDocumentItem(billingDocument, billingDocumentItems, billingMasterdata,
                    amount, tariffPpuInCent, null, true, false);
            return;
        }

        // ZVT (zeitbasierter Tarif): Konsistenz-Guard + je Bucket eine Position.
        validateZvtAllocation(billingMasterdata, allocation);

        for (AllocationBucket bucket : sortedBuckets(allocation.getBuckets())) {
            BigDecimal amount = BigDecimalTools.makeZeroIfNull(bucket.getKWh())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal price;
            String label;
            switch (bucket.getKey()) {
                case "BASE" -> {
                    price = billingMasterdata.getMeteringPointType()==MeteringPointType.CONSUMER ?
                            BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffWorkingFeePerConsumedkwh()) :
                            BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffCreditAmountPerProducedkwh());
                    label = "Tarif: Basis";
                }
                case "T1" -> {
                    price = billingMasterdata.getTariffTime1CentPerKwh();
                    label = buildTimeWindowLabel(billingMasterdata.getTariffTime1Name(),
                            billingMasterdata.getTariffTime1From(), billingMasterdata.getTariffTime1To());
                }
                case "T2" -> {
                    price = billingMasterdata.getTariffTime2CentPerKwh();
                    label = buildTimeWindowLabel(billingMasterdata.getTariffTime2Name(),
                            billingMasterdata.getTariffTime2From(), billingMasterdata.getTariffTime2To());
                }
                default -> throw new ZvtContractViolationException(String.format(
                        "Zaehlpunkt %s: unbekannter Bucket-Key '%s' (erwartet BASE/T1/T2).",
                        billingMasterdata.getMeteringPointId(), bucket.getKey()));
            }
            // freie kWh gelten nur im Einfach-Modus (Nutzer-Festlegung) und
            // werden hier bewusst nicht beruecksichtigt. keepZeroPosition=true:
            // jede Tarifoption (Basis/Zeitraum) erscheint auch bei 0 kWh.
            createEnergyDocumentItem(billingDocument, billingDocumentItems, billingMasterdata,
                    amount, BigDecimalTools.makeZeroIfNull(price), label, false, true);
        }
    }

    /** Anzeige-Label eines Zeitfensters: "Tarif: <Name> (HH:MM - HH:MM)"; leerer Name -> nur Zeitraum. */
    private static String buildTimeWindowLabel(String name, String from, String to) {
        String window = String.format("(%s - %s)", from, to);
        return StringUtils.isBlank(name) ? "Tarif: " + window : "Tarif: " + name.trim() + " " + window;
    }

    /** Buckets in stabiler Reihenfolge BASE, T1, T2 (Positionen und PDF-Zeilen). */
    private static List<AllocationBucket> sortedBuckets(AllocationBucket[] buckets) {
        return Arrays.stream(buckets).sorted(Comparator.comparingInt(
                b -> switch (String.valueOf(b.getKey())) {
                    case "BASE" -> 0;
                    case "T1" -> 1;
                    case "T2" -> 2;
                    default -> 3;
                })).toList();
    }

    /**
     * Fail-loud-Kontrakt fuer zeitbasierte Tarife (Fable-Review B2/B3):
     * buckets muessen vorhanden sein, die mitgesendeten timeWindows muessen
     * exakt den AKTUELLEN Masterdata-Fenstern entsprechen (die View ist live,
     * kein Snapshot), und Bucket-Keys muessen zu aktiven Fenstern gehoeren.
     */
    private void validateZvtAllocation(BillingMasterdata billingMasterdata, Allocation allocation) {
        final String zp = billingMasterdata.getMeteringPointId();

        if (allocation == null || allocation.getBuckets() == null || allocation.getBuckets().length == 0) {
            throw new ZvtContractViolationException(String.format(
                    "Zaehlpunkt %s: zeitbasierter Tarif, aber keine Fenster-Teilsummen (buckets) im Payload. " +
                    "Abrechnung abgebrochen - kein stiller Basispreis-Fallback.", zp));
        }

        boolean w1Active = Boolean.TRUE.equals(billingMasterdata.getTariffTime1Active());
        boolean w2Active = Boolean.TRUE.equals(billingMasterdata.getTariffTime2Active());

        if (w1Active && billingMasterdata.getTariffTime1CentPerKwh() == null) {
            throw new ZvtContractViolationException(String.format(
                    "Zaehlpunkt %s: Zeitfenster 1 aktiv, aber ohne Preis in den Tarif-Stammdaten.", zp));
        }
        if (w2Active && billingMasterdata.getTariffTime2CentPerKwh() == null) {
            throw new ZvtContractViolationException(String.format(
                    "Zaehlpunkt %s: Zeitfenster 2 aktiv, aber ohne Preis in den Tarif-Stammdaten.", zp));
        }

        // Konsistenz-Guard: mitgesendete Fenster == aktuelle Masterdata-Fenster
        Map<String, AllocationTimeWindow> sent = new HashMap<>();
        if (allocation.getTimeWindows() != null) {
            for (AllocationTimeWindow tw : allocation.getTimeWindows()) {
                if (sent.put(tw.getKey(), tw) != null) {
                    throw new ZvtContractViolationException(String.format(
                            "Zaehlpunkt %s: Zeitfenster-Key %s mehrfach im Payload.", zp, tw.getKey()));
                }
            }
        }
        assertWindowMatches(zp, "T1", w1Active,
                billingMasterdata.getTariffTime1From(), billingMasterdata.getTariffTime1To(), sent.get("T1"));
        assertWindowMatches(zp, "T2", w2Active,
                billingMasterdata.getTariffTime2From(), billingMasterdata.getTariffTime2To(), sent.get("T2"));

        // Bucket-Keys: eindeutig und nur fuer aktive Fenster
        Set<String> seenKeys = new HashSet<>();
        for (AllocationBucket bucket : allocation.getBuckets()) {
            String key = String.valueOf(bucket.getKey());
            if (!seenKeys.add(key)) {
                throw new ZvtContractViolationException(String.format(
                        "Zaehlpunkt %s: Bucket-Key %s mehrfach im Payload.", zp, key));
            }
            switch (key) {
                case "BASE" -> { /* immer erlaubt */ }
                case "T1" -> {
                    if (!w1Active) throw new ZvtContractViolationException(String.format(
                            "Zaehlpunkt %s: Bucket T1, aber Zeitfenster 1 ist im Tarif nicht aktiv.", zp));
                }
                case "T2" -> {
                    if (!w2Active) throw new ZvtContractViolationException(String.format(
                            "Zaehlpunkt %s: Bucket T2, aber Zeitfenster 2 ist im Tarif nicht aktiv.", zp));
                }
                default -> throw new ZvtContractViolationException(String.format(
                        "Zaehlpunkt %s: unbekannter Bucket-Key '%s' (erwartet BASE/T1/T2).", zp, key));
            }
        }
    }

    private static void assertWindowMatches(String zp, String key, boolean active,
                                            String masterFrom, String masterTo, AllocationTimeWindow sent) {
        if (active) {
            if (sent == null) {
                throw new ZvtContractViolationException(String.format(
                        "Zaehlpunkt %s: Zeitfenster %s ist im Tarif aktiv, fehlt aber im Payload (timeWindows). " +
                        "Tarif wurde vermutlich zwischenzeitlich geaendert - Abrechnung bitte neu starten.", zp, key));
            }
            if (!StringUtils.equals(StringUtils.trim(masterFrom), StringUtils.trim(sent.getFrom()))
                    || !StringUtils.equals(StringUtils.trim(masterTo), StringUtils.trim(sent.getTo()))) {
                throw new ZvtContractViolationException(String.format(
                        "Zaehlpunkt %s: Zeitfenster %s weicht von den aktuellen Tarif-Stammdaten ab " +
                        "(Payload %s-%s, Tarif %s-%s). Tarif wurde vermutlich zwischenzeitlich geaendert - " +
                        "Abrechnung bitte neu starten.", zp, key,
                        sent.getFrom(), sent.getTo(), masterFrom, masterTo));
            }
        } else if (sent != null) {
            throw new ZvtContractViolationException(String.format(
                    "Zaehlpunkt %s: Zeitfenster %s im Payload, ist im Tarif aber nicht aktiv. " +
                    "Tarif wurde vermutlich zwischenzeitlich geaendert - Abrechnung bitte neu starten.", zp, key));
        }
    }

    /** Kontraktverletzung zeitbasierter Tarif (ZVT) - fuehrt zum Lauf-Abbruch (FAILED im Async-Pfad). */
    public static class ZvtContractViolationException extends RuntimeException {
        public ZvtContractViolationException(String message) { super(message); }
    }

    /**
     * Erzeugt eine Energie-Position (Einfach: genau eine je ZP; ZVT: eine je
     * Bucket). Rabatt/USt/Rundung je Position wie bisher; freie kWh nur im
     * Einfach-Modus (applyFreeKwh).
     */
    private void createEnergyDocumentItem(BillingDocument billingDocument,
                                          List<BillingDocumentItem> billingDocumentItems,
                                          BillingMasterdata billingMasterdata,
                                          BigDecimal amount,
                                          BigDecimal tariffPpuInCent,
                                          String timeWindowLabel,
                                          boolean applyFreeKwh,
                                          boolean keepZeroPosition) {

        BillingDocumentItem newBillingDocumentItem = new BillingDocumentItem();
        newBillingDocumentItem.setMeteringPointId(billingMasterdata.getMeteringPointId());
        newBillingDocumentItem.setMeteringPointType(billingMasterdata.getMeteringPointType());
        newBillingDocumentItem.setText(timeWindowLabel == null
                ? buildItemText(billingMasterdata)
                : buildItemText(billingMasterdata) + "\n" + timeWindowLabel);
        final String documentText = billingMasterdata.getTariffText();
        newBillingDocumentItem.setDocumentText(StringUtils.isNotEmpty(documentText) ?
                documentText.replace("##", "\n"): documentText);
        newBillingDocumentItem.setTariffName(billingMasterdata.getTariffName());
        newBillingDocumentItem.setTariffId(billingMasterdata.getTariffId());
        newBillingDocumentItem.setTariffVersion(billingMasterdata.getTariffVersion());

        BigDecimal tariffFreekwh = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffFreekwh())
                .setScale(2, RoundingMode.HALF_UP);

        // Freie kWh berücksichtigen
        if (applyFreeKwh && billingMasterdata.getMeteringPointType()==MeteringPointType.CONSUMER &&
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

        BigDecimal discountPercent = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffDiscount());
        BigDecimal netValue = amount.multiply(tariffPpuInCent).divide(BigDecimal.valueOf(100.0))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountValue = netValue.multiply(discountPercent.divide(BigDecimal.valueOf(100.0)))
                .setScale(2, RoundingMode.HALF_UP);
        netValue = netValue.subtract(discountValue);
        BigDecimal vatPercent = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffVatInPercent());
        BigDecimal vatEuro = BigDecimal.valueOf(0);
        if (billingMasterdata.getTariffUseVat()) {
            vatEuro = netValue.multiply(vatPercent.divide(BigDecimal.valueOf(100.0)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal grossValue = netValue.add(vatEuro);

        // Nullpositionen werden i.d.R. unterdrueckt. ZVT-Positionen (Basis/Zeitraum)
        // werden bewusst AUCH bei 0 kWh gezeigt (keepZeroPosition), damit alle
        // konfigurierten Tarifoptionen sichtbar sind und keine zu fehlen scheint.
        if (!keepZeroPosition && BigDecimalTools.isNullOrZero(grossValue)) return;

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
     */
    private void createCustomBillingDocumentItem(BillingDocument billingDocument,
                                           List<BillingDocumentItem> billingDocumentItems,
                                           String text,
                                           String documentText,
                                           String tariffName,
                                           BigDecimal price,
                                           BigDecimal discountPercent,
                                           boolean useVat,
                                           BigDecimal vatPercent) {

        if (BigDecimalTools.isNullOrZero(price)) return;

        BillingDocumentItem newBillingDocumentItem = new BillingDocumentItem();

        BigDecimal discountPercentSafe = BigDecimalTools.makeZeroIfNull(discountPercent);
        BigDecimal discountValue = price.multiply(discountPercentSafe.divide(BigDecimal.valueOf(100.0)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netValue = price.subtract(discountValue);
        BigDecimal vatPercentSafe = BigDecimalTools.makeZeroIfNull(vatPercent);
        BigDecimal vatEuro = useVat ? netValue
                .multiply(vatPercentSafe.divide(BigDecimal.valueOf(100.0)))
                .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(0);
        BigDecimal grossValue = netValue.add(vatEuro);

        if (BigDecimalTools.isNullOrZero(grossValue)) return; // Keine Nullposition!

        newBillingDocumentItem.setText(text);
        newBillingDocumentItem.setDocumentText(documentText);
        newBillingDocumentItem.setTariffName(tariffName);
        newBillingDocumentItem.setAmount(BigDecimal.ONE);
        newBillingDocumentItem.setPricePerUnit(price);
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
     */
    private void createMeteringPointFeeDocumentItem(BillingDocument billingDocument,
                                                    List<BillingDocumentItem> billingDocumentItems,
                                                    BillingMasterdata billingMasterdata) {
        BigDecimal pricePerMeter = billingMasterdata.getTariffMeteringPointFee();
        if (BigDecimalTools.isNullOrZero(pricePerMeter)) return;

        BigDecimal vatPercent;
        boolean useVat;
        if (billingMasterdata.getMeteringPointType() == MeteringPointType.CONSUMER) {
            // Fuer Verbraucher Zaehlpunkte wird der UST Satz vom (Verbraucher-)Tarif genommen
            // = USt Satz der EEG
            useVat = billingMasterdata.getTariffUseVat();
            vatPercent = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffVatInPercent());
        } else {
            // Fuer Erzeuger Zaehlpunkte wird der UST Satz aus dem ZP-Ust Feld genommen
            // da der UST Satz des (Erzeuger-) Tarifes der UST Satz des Erzeugers enthaelt und
            // nicht den der EEG.
            vatPercent = BigDecimalTools.makeZeroIfNull(billingMasterdata.getTariffMeteringPointVat());
            useVat = vatPercent.compareTo(BigDecimal.ZERO)>0;
        }

        BillingDocumentItem newBillingDocumentItem = new BillingDocumentItem();

        BigDecimal vatPercentSafe = BigDecimalTools.makeZeroIfNull(vatPercent);
        BigDecimal vatEuro = useVat ? pricePerMeter
                .multiply(vatPercentSafe.divide(BigDecimal.valueOf(100.0)))
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal grossValue = pricePerMeter.add(vatEuro);

        if (BigDecimalTools.isNullOrZero(grossValue)) return; // Keine Nullposition!

        // Gruppierungsschluessel fuer die ZP-Bloecke im PDF; ParticipantAmountService
        // nimmt Gebuehren-Items weiterhin per Text-Prefix aus den Energie-Betraegen aus.
        newBillingDocumentItem.setMeteringPointId(billingMasterdata.getMeteringPointId());
        newBillingDocumentItem.setText(String.format(ZAEHLPUNKTGEBUEHR_TEXT + ": %s", billingMasterdata.getMeteringPointId()));
        final String documentText = billingMasterdata.getTariffMeteringPointFeeText();
        newBillingDocumentItem.setDocumentText(StringUtils.isNotEmpty(documentText) ?
                documentText.replace("##", "\n"): documentText);
        newBillingDocumentItem.setTariffName(ZAEHLPUNKTGEBUEHR_TEXT);
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
