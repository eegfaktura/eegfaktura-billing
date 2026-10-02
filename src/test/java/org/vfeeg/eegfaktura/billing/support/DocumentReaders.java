package org.vfeeg.eegfaktura.billing.support;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads the generated outputs in tests: PDF text (PDFBox), XLSX sheets (POI) and the ZIP archive. */
public final class DocumentReaders {

    private DocumentReaders() {
    }

    /** The text of all pages, whitespace runs folded to one blank. */
    public static String pdfText(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Data rows (header excluded) of one sheet. */
    public static int dataRows(byte[] xlsx, String sheetName) {
        return withSheet(xlsx, sheetName, sheet -> sheet.getLastRowNum());
    }

    /** Sum of a numeric column over the data rows, rounded to cents. */
    public static BigDecimal columnSum(byte[] xlsx, String sheetName, int column) {
        return withSheet(xlsx, sheetName, sheet -> {
            BigDecimal sum = BigDecimal.ZERO;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Cell cell = sheet.getRow(r).getCell(column);
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    sum = sum.add(BigDecimal.valueOf(cell.getNumericCellValue()));
                }
            }
            return sum.setScale(2, RoundingMode.HALF_UP);
        });
    }

    /** The text of one header cell, to pin the column a sum is taken from. */
    public static String header(byte[] xlsx, String sheetName, int column) {
        return withSheet(xlsx, sheetName, sheet -> {
            Row header = sheet.getRow(0);
            return header.getCell(column).getStringCellValue();
        });
    }

    /** Names and contents of the entries of a ZIP archive. */
    public static List<byte[]> zipEntries(byte[] zip, List<String> namesOut) {
        List<byte[]> contents = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                namesOut.add(entry.getName());
                contents.add(in.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return contents;
    }

    private interface SheetFunction<T> {
        T apply(Sheet sheet);
    }

    private static <T> T withSheet(byte[] xlsx, String sheetName, SheetFunction<T> function) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new AssertionError("no sheet " + sheetName);
            }
            return function.apply(sheet);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
