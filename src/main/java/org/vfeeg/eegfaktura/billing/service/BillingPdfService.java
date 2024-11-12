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
        ArrayList<Map<String,?>> itemsParameters = new ArrayList<>();
        ArrayList<String> documentTextsFromTariffs = new ArrayList<>();
        for (BillingDocumentItem billingDocumentItem : items.stream().sorted(
                Comparator.comparing(BillingDocumentItem::getText)).toList()) {
            HashMap<String,String> itemParameters = createParamMapForItem(billingDocumentItem);
            itemsParameters.add(itemParameters);
            String billingDocumentItemDocumentText = billingDocumentItem.getDocumentText();
            if (!ObjectUtils.isEmpty(billingDocumentItemDocumentText)
                    && !documentTextsFromTariffs.contains(billingDocumentItemDocumentText)) {
                documentTextsFromTariffs.add(billingDocumentItemDocumentText);
            }
        }
        itemsParameters.sort(Comparator.comparing(o -> ((String) o.get("text"))));
        parameters.put("afterItemsText", StringTools.nullSafeJoin("\n"
                , String.join("\n", documentTextsFromTariffs)
                , document.getAfterItemsText()));

        parameters.put("items", new JRMapCollectionDataSource(itemsParameters));

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
