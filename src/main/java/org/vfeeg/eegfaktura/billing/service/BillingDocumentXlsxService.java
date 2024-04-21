package org.vfeeg.eegfaktura.billing.service;

import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.domain.MeteringPointType;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional

public class BillingDocumentXlsxService {

    private final BillingDocumentRepository billingDocumentRepository;
    private final BillingDocumentItemRepository billingDocumentItemRepository;

    public BillingDocumentXlsxService(BillingDocumentRepository billingDocumentRepository,
                                      BillingDocumentItemRepository billingDocumentItemRepository) {
        this.billingDocumentRepository = billingDocumentRepository;
        this.billingDocumentItemRepository = billingDocumentItemRepository;
    }

    private void createXlsxHeaderList(XSSFWorkbook xssfWorkbook, XSSFSheet xssfSheet) {
        Row row = xssfSheet.createRow(0);
        CellStyle style = xssfWorkbook.createCellStyle();
        XSSFFont font = xssfWorkbook.createFont();
        font.setBold(true);
        font.setFontHeight(12);
        style.setFont(font);
        int columnNumber = 0;
        createCell(xssfSheet, row, columnNumber++, "Dokumenttyp", style);
        createCell(xssfSheet, row, columnNumber++, "Nummer", style);
        createCell(xssfSheet, row, columnNumber++, "Datum", style);
        createCell(xssfSheet, row, columnNumber++, "Abrechnung", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Name", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Mitgliedsnummer", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Adresse 1", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Adresse 2", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Adresse 3", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Kontoeigner", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Konto IBAN", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Mandatsausstellung", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Mandatsreferenz", style);
        createCell(xssfSheet, row, columnNumber++, "Ersteller Name", style);
        createCell(xssfSheet, row, columnNumber++, "Ersteller BankName", style);
        createCell(xssfSheet, row, columnNumber++, "Ersteller IBAN", style);
        createCell(xssfSheet, row, columnNumber++, "UST Satz 1 (%)", style);
        createCell(xssfSheet, row, columnNumber++, "UST Satz 1 Summe (Euro)", style);
        createCell(xssfSheet, row, columnNumber++, "UST Satz 2 (%)", style);
        createCell(xssfSheet, row, columnNumber++, "UST Satz 2 Summe (Euro)", style);
        createCell(xssfSheet, row, columnNumber++, "Rechnungsbetrag Netto", style);
        createCell(xssfSheet, row, columnNumber++, "Rechnungsbetrag Brutto", style);
    }

    private void createXlsxHeaderDetails(XSSFWorkbook xssfWorkbook, XSSFSheet xssfSheet) {
        Row row = xssfSheet.createRow(0);
        CellStyle style = xssfWorkbook.createCellStyle();
        XSSFFont font = xssfWorkbook.createFont();
        font.setBold(true);
        font.setFontHeight(12);
        style.setFont(font);
        int columnNumber = 0;
        createCell(xssfSheet, row, columnNumber++, "Dokumenttyp", style);
        createCell(xssfSheet, row, columnNumber++, "Nummer", style);
        createCell(xssfSheet, row, columnNumber++, "Datum", style);
        createCell(xssfSheet, row, columnNumber++, "Abrechnung", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Name", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Mitgliedsnummer", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Adresse 1", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Adresse 2", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Adresse 3", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Kontoeigner", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Konto IBAN", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Mandatsausstellung", style);
        createCell(xssfSheet, row, columnNumber++, "Empfänger Mandatsreferenz", style);
        createCell(xssfSheet, row, columnNumber++, "Ersteller Name", style);
        createCell(xssfSheet, row, columnNumber++, "Ersteller BankName", style);
        createCell(xssfSheet, row, columnNumber++, "Ersteller IBAN", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Zählpunkttyp", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Text", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Menge", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Mengeneinheit", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Preis / Einheit", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Preis / Einheit Euro/Cent", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Rabatt %", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Nettobetrag", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. UST %", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. UST Betrag", style);
        createCell(xssfSheet, row, columnNumber++, "Pos. Bruttobetrag", style);
    }
    private void createCell(XSSFSheet xssfSheet, Row row, int columnNumber, Object valueOfCell, CellStyle style) {
        xssfSheet.autoSizeColumn(columnNumber);
        Cell cell = row.createCell(columnNumber);
        if (valueOfCell instanceof Integer) {
            cell.setCellValue((Integer) valueOfCell);
        } else if (valueOfCell instanceof Long) {
            cell.setCellValue((Long) valueOfCell);
        } else if (valueOfCell instanceof String) {
            cell.setCellValue((String) valueOfCell);
        } else if (valueOfCell instanceof LocalDate){
            cell.setCellValue((LocalDate) valueOfCell);
        } else if (valueOfCell instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal)valueOfCell)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        cell.setCellStyle(style);
    }
    private void createRowsList(XSSFWorkbook xssfWorkbook, XSSFSheet xssfSheet,
                                List<BillingDocument> billingDocuments) {
        int rowCount = 1;
        CellStyle style = xssfWorkbook.createCellStyle();
        XSSFFont font = xssfWorkbook.createFont();
        font.setFontHeight(10);
        style.setFont(font);

        CellStyle dateStyle = xssfWorkbook.createCellStyle();
        CreationHelper createHelper = xssfWorkbook.getCreationHelper();
        short format = createHelper.createDataFormat().getFormat("dd.mm.yy");
        XSSFFont dateStylefont = xssfWorkbook.createFont();
        dateStylefont.setFontHeight(10);
        dateStyle.setDataFormat(format);
        dateStyle.setFont(dateStylefont);

        for (BillingDocument billingDocument : billingDocuments) {
            Row row = xssfSheet.createRow(rowCount++);
            int columnNumber = 0;
            createCell(xssfSheet, row, columnNumber++,
                    BillingDocument.getDocumentTypeName(billingDocument.getBillingDocumentType()), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getDocumentNumber(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getDocumentDate(), dateStyle);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getClearingPeriodIdentifier(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientName(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientParticipantNumber(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientAddressLine1(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientAddressLine2(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientAddressLine3(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientBankOwner(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientBankIban(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientSepaMandateIssueDate(), dateStyle);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getRecipientSepaMandateReference(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getIssuerName(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getIssuerBankName(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getIssuerBankIBAN(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getVat1Percent(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getVat1SumInEuro(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getVat2Percent(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getVat2SumInEuro(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getNetAmountInEuro(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocument.getGrossAmountInEuro(), style);
        }
    }

    private void createRowsDetails(XSSFWorkbook xssfWorkbook, XSSFSheet xssfSheet,
                                List<BillingDocumentItem> billingDocumentItems) {
        int rowCount = 1;
        CellStyle style = xssfWorkbook.createCellStyle();
        XSSFFont font = xssfWorkbook.createFont();
        font.setFontHeight(10);
        style.setFont(font);

        CellStyle dateStyle = xssfWorkbook.createCellStyle();
        CreationHelper createHelper = xssfWorkbook.getCreationHelper();
        short format = createHelper.createDataFormat().getFormat("dd.mm.yy");
        XSSFFont dateStylefont = xssfWorkbook.createFont();
        dateStylefont.setFontHeight(10);
        dateStyle.setDataFormat(format);
        dateStyle.setFont(dateStylefont);

        for (BillingDocumentItem billingDocumentItem : billingDocumentItems) {
            Row row = xssfSheet.createRow(rowCount++);
            int columnNumber = 0;
            createCell(xssfSheet, row, columnNumber++,
                    BillingDocument.getDocumentTypeName(billingDocumentItem.getBillingDocument().getBillingDocumentType()), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getDocumentNumber(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getDocumentDate(), dateStyle);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getClearingPeriodIdentifier(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientName(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientParticipantNumber(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientAddressLine1(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientAddressLine2(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientAddressLine3(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientBankOwner(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientBankIban(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientSepaMandateIssueDate(), dateStyle);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getRecipientSepaMandateReference(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getIssuerName(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getIssuerBankName(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getBillingDocument().getIssuerBankIBAN(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getMeteringPointType() != null ?
                    billingDocumentItem.getMeteringPointType() == MeteringPointType.CONSUMER ?
                            "Erzeuger" : "Verbraucher" : "" , style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getText(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getAmount(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getMeteringPointType()!=null ?
                    "kWh" : "", style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getPricePerUnit(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getPpuUnit()!=null ?
                    billingDocumentItem.getPpuUnit() : "Ct", style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getDiscountPercent(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getNetValue(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getVatPercent(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getVatValueInEuro(), style);
            createCell(xssfSheet, row, columnNumber++, billingDocumentItem.getGrossValue(), style);
        }
    }
    public byte[] createXlsx (UUID billingRunId) throws IOException {

        List<BillingDocument> billingDocuments = billingDocumentRepository.findByBillingRunId(billingRunId);
        List<BillingDocumentItem> billingDocumentItems = billingDocumentItemRepository.findByBillingRunId(billingRunId);

        XSSFWorkbook xssfWorkbook = new XSSFWorkbook();

        XSSFSheet xssfSheetList = xssfWorkbook.createSheet("Liste");
        createXlsxHeaderList(xssfWorkbook, xssfSheetList);
        createRowsList(xssfWorkbook, xssfSheetList, billingDocuments);

        XSSFSheet xssfSheetDetails = xssfWorkbook.createSheet("Details");
        createXlsxHeaderDetails(xssfWorkbook, xssfSheetDetails);
        createRowsDetails(xssfWorkbook, xssfSheetDetails, billingDocumentItems);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        xssfWorkbook.write(byteArrayOutputStream);
        xssfWorkbook.close();
        byteArrayOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }
}
