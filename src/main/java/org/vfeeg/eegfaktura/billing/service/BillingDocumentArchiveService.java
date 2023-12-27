package org.vfeeg.eegfaktura.billing.service;

import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional
public class BillingDocumentArchiveService {

    BillingDocumentFileRepository billingDocumentFileRepository;
    FileDataRepository fileDataRepository;

    public BillingDocumentArchiveService(final BillingDocumentFileRepository billingDocumentFileRepository,
                                         final FileDataRepository fileDataRepository) {
        this.billingDocumentFileRepository = billingDocumentFileRepository;
        this.fileDataRepository = fileDataRepository;
    }

    public byte[] createArchive (UUID billingRunId) throws IOException {

        List<BillingDocumentFile> billingDocumentFiles = billingDocumentFileRepository
                .findByBillingRunId(billingRunId);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (BillingDocumentFile billingDocumentFile : billingDocumentFiles) {
            Optional<FileData> fileDataOptional = fileDataRepository.findById(billingDocumentFile.getFileDataId());
            if (fileDataOptional.isPresent()) {
                ZipEntry zipEntry = new ZipEntry(Math.abs(billingDocumentFile.getId().hashCode())+"_"+billingDocumentFile.getName());
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(fileDataOptional.get().getData());
                zipOutputStream.closeEntry();
            }
        }
        zipOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }
}
