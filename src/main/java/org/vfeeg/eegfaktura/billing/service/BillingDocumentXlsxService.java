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
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional

public class BillingDocumentXlsxService {

    private void createXlsxHeader(XSSFWorkbook xssfWorkbook, XSSFSheet xssfSheet) {
        Row row = xssfSheet.createRow(0);
        CellStyle style = xssfWorkbook.createCellStyle();
        XSSFFont font = xssfWorkbook.createFont();
        font.setBold(true);
        font.setFontHeight(16);
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
            cell.setCellValue((double) ((BigDecimal)valueOfCell)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        cell.setCellStyle(style);
    }
    private void createRows(XSSFWorkbook xssfWorkbook, XSSFSheet xssfSheet,
                            List<BillingDocumentDTO> billingDocumentDTOs) {
        int rowCount = 1;
        CellStyle style = xssfWorkbook.createCellStyle();
        XSSFFont font = xssfWorkbook.createFont();
        font.setFontHeight(14);
        style.setFont(font);

        CellStyle dateStyle = xssfWorkbook.createCellStyle();
        CreationHelper createHelper = xssfWorkbook.getCreationHelper();
        short format = createHelper.createDataFormat().getFormat("dd.mm.yy");
        XSSFFont dateStylefont = xssfWorkbook.createFont();
        dateStylefont.setFontHeight(14);
        dateStyle.setDataFormat(format);
        dateStyle.setFont(dateStylefont);

        for (BillingDocumentDTO billingDocument : billingDocumentDTOs) {
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
    public byte[] createXlsx (List<BillingDocumentDTO> billingDocumentDTOs) throws IOException {
        XSSFWorkbook xssfWorkbook = new XSSFWorkbook();
        XSSFSheet xssfSheet = xssfWorkbook.createSheet("Liste");
        createXlsxHeader(xssfWorkbook, xssfSheet);
        createRows(xssfWorkbook, xssfSheet, billingDocumentDTOs);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        xssfWorkbook.write(byteArrayOutputStream);
        xssfWorkbook.close();
        byteArrayOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }
}
