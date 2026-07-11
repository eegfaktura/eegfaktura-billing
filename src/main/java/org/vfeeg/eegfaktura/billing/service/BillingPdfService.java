package org.vfeeg.eegfaktura.billing.service;

import jakarta.transaction.Transactional;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.vfeeg.eegfaktura.billing.domain.*;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.BigDecimalTools;
import org.vfeeg.eegfaktura.billing.util.ClearingPeriodIdentifierTool;
import org.vfeeg.eegfaktura.billing.util.StringTools;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class BillingPdfService {

    private final static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static JasperReport defaultReport;

    private final BillingDocumentFileRepository billingDocumentFileRepository;
    private final FileDataRepository fileDataRepository;

    public BillingPdfService(BillingDocumentFileRepository fileRepository,
                             FileDataRepository fileDataRepository) {
        this.billingDocumentFileRepository = fileRepository;
        this.fileDataRepository = fileDataRepository;
    }

    private JasperReport getDefaultReport() {
        if (defaultReport == null) {
            try (InputStream defaultTemplateInputStream = new ClassPathResource("BillingDocumentDefaultTemplate.jrxml")
                    .getInputStream()) {
                defaultReport = JasperCompileManager.compileReport(defaultTemplateInputStream);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create and save PDF for BillingDocument due to "+e.getMessage(), e);
            }
        }
        return defaultReport;
    }

    public BillingDocumentFile createAndSavePDF(final BillingDocument document, final List<BillingDocumentItem> items,
                                                final Map<String, String> meteringPointNames,
                                                final UUID headerImageFileDataId, final UUID footerImageFileDataId,
                                                boolean isPreview)  {

        JasperReport report = getDefaultReport();

        //populate parameters map
        HashMap<String,Object> parameters = new HashMap<>();
        parameters.put("issuerName", document.getIssuerName());
        parameters.put("issuerAddressLine1", document.getIssuerAddressLine1());
        parameters.put("issuerAddressLine2", document.getIssuerAddressLine2());
        parameters.put("issuerAddressLine3", document.getIssuerAddressLine3());
        parameters.put("issuerTaxId", document.getIssuerTaxId());
        parameters.put("issuerVatId", document.getIssuerVatId());
        parameters.put("issuerPhone", document.getIssuerPhone());
        parameters.put("issuerMail", document.getIssuerMail());
        parameters.put("issuerWebsite", document.getIssuerWebsite());
        parameters.put("issuerCompanyRegisterNumber", document.getIssuerCompanyRegisterNumber());
        parameters.put("issuerBankCreditorId", document.getIssuerBankCreditorId());
        parameters.put("documentType", BillingDocument.getDocumentTypeName(document.getBillingDocumentType())
                .toUpperCase());
        parameters.put("documentDate", document.getDocumentDate().toString());
        parameters.put("documentNumber", document.getDocumentNumber());
        parameters.put("clearingPeriodIdentifier", ClearingPeriodIdentifierTool.asText(document.getClearingPeriodIdentifier()));
        parameters.put("recipientName", document.getRecipientName());
        parameters.put("recipientParticipantNumber", document.getRecipientParticipantNumber());
        parameters.put("recipientBankName", document.getRecipientBankName());
        parameters.put("recipientBankIban", "******" + (document.getRecipientBankIban() != null ?
                document.getRecipientBankIban().substring(Math.max(0, document.getRecipientBankIban().length() - 4))
                : ""));
        parameters.put("recipientBankOwner", document.getRecipientBankOwner());
        parameters.put("recipientSepaMandateReference", document.getRecipientSepaMandateReference());
        parameters.put("recipientTaxId", document.getRecipientTaxId());
        parameters.put("recipientVatId", document.getRecipientVatId());
        parameters.put("recipientAddressLine1", document.getRecipientAddressLine1());
        parameters.put("recipientAddressLine2", document.getRecipientAddressLine2());
        parameters.put("recipientAddressLine3", document.getRecipientAddressLine3());
        if (!BigDecimalTools.isNullOrZero(document.getVat1Percent())) {
            parameters.put("vat1Percent", BigDecimalTools.makeGermanString(document.getVat1Percent(), "%"));
            parameters.put("vat1SumInEuro", BigDecimalTools.makeGermanString(document.getVat1SumInEuro(), "€"));
        }
        if (!BigDecimalTools.isNullOrZero(document.getVat2Percent())) {
            parameters.put("vat2Percent", BigDecimalTools.makeGermanString(document.getVat2Percent(), "%"));
            parameters.put("vat2SumInEuro", BigDecimalTools.makeGermanString(document.getVat2SumInEuro(), "€"));
        }
        parameters.put("grossAmountInEuro", BigDecimalTools.makeGermanString(document.getGrossAmountInEuro(), "€"));
        parameters.put("netAmountInEuro", BigDecimalTools.makeGermanString(document.getNetAmountInEuro(), "€"));
        parameters.put("beforeItemsText", document.getBeforeItemsText());
        parameters.put("termsText", document.getTermsText());
        parameters.put("footerText", document.getFooterText());

        if (headerImageFileDataId!=null) {
            Optional<FileData> headerImageFileData = fileDataRepository.findById(headerImageFileDataId);
            headerImageFileData.ifPresent(fileData -> parameters.put("logo", fileData.getData()));
        }
        if (footerImageFileDataId!=null) {
            Optional<FileData> footerImageFileData = fileDataRepository.findById(footerImageFileDataId);
            footerImageFileData.ifPresent(fileData -> parameters.put("footerImage", fileData.getData()));
        }
        // Dokumenttexte der Tarife sammeln (unveraendert), Items dagegen je
        // Zaehlpunkt als Block rendern: Kopfzeile, Positionszeilen, Zwischensumme.
        ArrayList<String> documentTextsFromTariffs = new ArrayList<>();
        for (BillingDocumentItem billingDocumentItem : items) {
            String billingDocumentItemDocumentText = billingDocumentItem.getDocumentText();
            if (!ObjectUtils.isEmpty(billingDocumentItemDocumentText)
                    && !documentTextsFromTariffs.contains(billingDocumentItemDocumentText)) {
                documentTextsFromTariffs.add(billingDocumentItemDocumentText);
            }
        }
        parameters.put("afterItemsText", StringTools.nullSafeJoin("\n"
                , String.join("\n", documentTextsFromTariffs)
                , document.getAfterItemsText()));

        parameters.put("items", new JRMapCollectionDataSource(
                buildGroupedItemRows(items, meteringPointNames)));

        byte[] pdfDataBytes;
        try {
            JasperPrint jprint = JasperFillManager.fillReport(report, parameters, new JREmptyDataSource());
            pdfDataBytes = JasperExportManager.exportReportToPdf(jprint);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create report due to "+e.getMessage(), e);
        }

        String pdfFilename = StringTools.nullSafeJoin("_", document.getClearingPeriodIdentifier(),
                BillingDocument.getDocumentTypeName(document.getBillingDocumentType()),
                isPreview ? "Vorschau" : (document.getBillingDocumentType()==BillingDocumentType.INFO
                        ? dateTimeFormatter.format(document.getDocumentDate())
                        : document.getDocumentNumber())
        )+".pdf";

        String mimeType = MediaType.APPLICATION_PDF_VALUE;

        FileData pdfFileData = new FileData();
        pdfFileData.setName(pdfFilename);
        pdfFileData.setMimeType(mimeType);
        pdfFileData.setTenantId(document.getTenantId());
        pdfFileData.setData(pdfDataBytes);
        pdfFileData = fileDataRepository.save(pdfFileData);

        BillingDocumentFile pdfFile = new BillingDocumentFile();
        pdfFile.setName(pdfFilename);
        pdfFile.setMimeType(mimeType);
        pdfFile.setTenantId(document.getTenantId());
        pdfFile.setBillingDocument(document);
        pdfFile.setFileDataId(pdfFileData.getId());
        return billingDocumentFileRepository.save(pdfFile);

    }

    /**
     * Baut die Tabellenzeilen: je Zaehlpunkt ein Block (Kopfzeile fett,
     * Positionszeilen, Zwischensumme fett), danach die Teilnehmer-Positionen
     * ohne Zaehlpunkt (z.B. Mitgliedsbeitrag). Die Detailzellen des Templates
     * rendern styled markup - alle Texte werden escaped, fette Zeilen in
     * <b>...</b> gesetzt.
     */
    private ArrayList<Map<String,?>> buildGroupedItemRows(List<BillingDocumentItem> items,
                                                          Map<String, String> meteringPointNames) {
        ArrayList<Map<String,?>> rows = new ArrayList<>();

        // Bloecke: alle Items mit Zaehlpunkt, sortiert nach ZP-Nummer;
        // Reihenfolge innerhalb des Blocks = Erzeugungsreihenfolge (Basis, T1, T2, Gebuehr).
        Map<String, List<BillingDocumentItem>> blocks = new TreeMap<>();
        List<BillingDocumentItem> participantLevelItems = new ArrayList<>();
        for (BillingDocumentItem item : items) {
            if (item.getMeteringPointId() != null) {
                blocks.computeIfAbsent(item.getMeteringPointId(), k -> new ArrayList<>()).add(item);
            } else {
                participantLevelItems.add(item);
            }
        }

        for (Map.Entry<String, List<BillingDocumentItem>> block : blocks.entrySet()) {
            String meteringPointId = block.getKey();
            List<BillingDocumentItem> blockItems = block.getValue();

            // Rabatt der Energie-Positionen (ein Rabatt fuer alle) -> Kopfzeile
            BigDecimal discountPercent = blockItems.stream()
                    .filter(i -> !isMeteringPointFeeItem(i))
                    .map(BillingDocumentItem::getDiscountPercent)
                    .filter(d -> !BigDecimalTools.isNullOrZero(d))
                    .findFirst().orElse(null);

            String name = meteringPointNames != null ? meteringPointNames.get(meteringPointId) : null;
            StringBuilder header = new StringBuilder("Zählpunkt ").append(meteringPointId);
            if (!ObjectUtils.isEmpty(name)) {
                header.append(" - ").append(name);
            }
            if (discountPercent != null) {
                header.append(" (Rabatt ").append(BigDecimalTools.makeGermanString(discountPercent, "%")).append(")");
            }
            rows.add(labelOnlyRow("<b>" + escapeStyled(header.toString()) + "</b>"));

            BigDecimal netSum = BigDecimal.ZERO;
            BigDecimal vatSum = BigDecimal.ZERO;
            BigDecimal grossSum = BigDecimal.ZERO;
            for (BillingDocumentItem item : blockItems) {
                HashMap<String, String> row = createParamMapForItem(item);
                row.put("text", escapeStyled(blockRowText(item, meteringPointId)));
                // Rabatt steht in der Blockkopfzeile - Zeilen-Suffix unterdruecken
                row.put("discountPercent", "0,00 %");
                rows.add(row);
                netSum = netSum.add(BigDecimalTools.makeZeroIfNull(item.getNetValue()));
                vatSum = vatSum.add(BigDecimalTools.makeZeroIfNull(item.getVatValueInEuro()));
                grossSum = grossSum.add(BigDecimalTools.makeZeroIfNull(item.getGrossValue()));
            }

            HashMap<String, String> subtotal = labelOnlyRow(
                    "<b>" + escapeStyled("Zwischensumme Zählpunkt " + meteringPointId) + "</b>");
            subtotal.put("netValue", "<b>" + escapeStyled(BigDecimalTools.makeGermanString(netSum, "€")) + "</b>");
            subtotal.put("vatPercent", "<b>" + escapeStyled(BigDecimalTools.makeGermanString(vatSum, "€")) + "</b>");
            subtotal.put("grossValue", "<b>" + escapeStyled(BigDecimalTools.makeGermanString(grossSum, "€")) + "</b>");
            rows.add(subtotal);
        }

        // Teilnehmer-Positionen (z.B. Mitgliedsbeitrag) nach den Bloecken
        participantLevelItems.sort(Comparator.comparing(BillingDocumentItem::getText));
        for (BillingDocumentItem item : participantLevelItems) {
            HashMap<String, String> row = createParamMapForItem(item);
            row.put("text", escapeStyled(row.get("text")));
            rows.add(row);
        }
        return rows;
    }

    /** Positionstext einer Blockzeile ohne die Kopf-Redundanz (ZP-Id/Anlage-Zeilen). */
    private static String blockRowText(BillingDocumentItem item, String meteringPointId) {
        if (isMeteringPointFeeItem(item)) {
            return BillingService.ZAEHLPUNKTGEBUEHR_TEXT;
        }
        String text = item.getText() != null ? item.getText() : "";
        List<String> remaining = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals(meteringPointId)
                    || trimmed.startsWith("Anlage-Name: ")
                    || trimmed.startsWith("Anlage-Nr.: ")) {
                continue;
            }
            if (!trimmed.isEmpty()) {
                remaining.add(trimmed);
            }
        }
        if (!remaining.isEmpty()) {
            return String.join("\n", remaining);
        }
        // Einfach-Tarif ohne Zusatzzeilen: Tarifname als Zeilenlabel
        return ObjectUtils.isEmpty(item.getTariffName()) ? "Energiemenge" : "Tarif: " + item.getTariffName();
    }

    private static boolean isMeteringPointFeeItem(BillingDocumentItem item) {
        return item.getText() != null && item.getText().startsWith(BillingService.ZAEHLPUNKTGEBUEHR_TEXT);
    }

    private static HashMap<String, String> labelOnlyRow(String styledText) {
        HashMap<String, String> row = new HashMap<>();
        row.put("text", styledText);
        row.put("amount", "");
        row.put("pricePerUnit", "");
        row.put("netValue", "");
        row.put("vatPercent", "");
        row.put("vatValueInEuro", "");
        row.put("grossValue", "");
        row.put("discountPercent", "0,00 %"); // unterdrueckt den "Rabatt:"-Zeilen-Suffix
        return row;
    }

    /** XML-Escaping fuer styled-markup Textzellen. */
    private static String escapeStyled(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private HashMap<String,String> createParamMapForItem(BillingDocumentItem billingDocumentItem) {
        HashMap<String, String> parameterMap = new HashMap<>();
        String ppuUnit = billingDocumentItem.getPpuUnit();
        parameterMap.put("text", billingDocumentItem.getText());
        parameterMap.put("pricePerUnit", BigDecimalTools.makeGermanString(billingDocumentItem.getPricePerUnit(),
                ppuUnit != null ? ppuUnit : "ct"));
        parameterMap.put("netValue", BigDecimalTools.makeGermanString(billingDocumentItem.getNetValue(), "€"));
        parameterMap.put("discountPercent", BigDecimalTools.makeGermanString(billingDocumentItem.getDiscountPercent(), "%"));
        parameterMap.put("vatPercent", BigDecimalTools.makeGermanString(billingDocumentItem.getVatPercent(), "%"));
        parameterMap.put("vatValueInEuro", BigDecimalTools.makeGermanString(billingDocumentItem.getVatValueInEuro(), "€"));
        parameterMap.put("grossValue", BigDecimalTools.makeGermanString(billingDocumentItem.getGrossValue(), "€"));
        if (billingDocumentItem.getMeteringPointType()!=null) {
            parameterMap.put("amount", BigDecimalTools.makeGermanString(billingDocumentItem.getAmount(), "kWh"));
        } else {
            parameterMap.put("amount", "");
        }
        return parameterMap;
    }



}
