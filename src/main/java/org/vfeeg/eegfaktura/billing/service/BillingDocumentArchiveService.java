package org.vfeeg.eegfaktura.billing.service;

import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

        List<BillingDocumentFile> billingDocumentFiles = new ArrayList<>(billingDocumentFileRepository
                .findByBillingRunId(billingRunId));
        // Stabile Reihenfolge, damit " (2)", " (3)" ... bei jedem Download gleich vergeben werden.
        billingDocumentFiles.sort(Comparator.comparing(BillingDocumentArchiveService::entryName)
                .thenComparing(BillingDocumentFile::getId));

        Set<String> usedEntryNames = new HashSet<>();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        for (BillingDocumentFile billingDocumentFile : billingDocumentFiles) {
            Optional<FileData> fileDataOptional = fileDataRepository.findById(billingDocumentFile.getFileDataId());
            if (fileDataOptional.isPresent()) {
                ZipEntry zipEntry = new ZipEntry(uniqueEntryName(entryName(billingDocumentFile), usedEntryNames));
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(fileDataOptional.get().getData());
                zipOutputStream.closeEntry();
            }
        }
        zipOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Name im ZIP: der gespeicherte Dateiname; Vorschau- und Info-Belege (ohne eigene Belegnummer)
     * zusaetzlich mit der Mitgliedsnummer vor der Dateiendung.
     */
    static String entryName(BillingDocumentFile billingDocumentFile) {
        // ein Altbestand ohne Namen bricht das ZIP nicht ab (vorher: "<hash>_null")
        String name = Objects.toString(billingDocumentFile.getName());
        BillingDocument billingDocument = billingDocumentFile.getBillingDocument();
        boolean withoutOwnNumber = billingDocument.getBillingDocumentType() == BillingDocumentType.INFO
                || StringUtils.isBlank(billingDocument.getDocumentNumber());
        if (!withoutOwnNumber) {
            return name;
        }
        String participantNumber = StringUtils.trimToEmpty(billingDocument.getRecipientParticipantNumber())
                .replaceAll("[/\\\\:*?\"<>|]", "_");
        return participantNumber.isEmpty() ? name : withSuffix(name, "_" + participantNumber);
    }

    /**
     * Haengt bei einem schon vergebenen Namen " (2)", " (3)" ... vor der Dateiendung an - bewusst anders als
     * "_&lt;Mitgliedsnummer&gt;", damit ein Zaehler nie wie eine Mitgliedsnummer aussieht.
     */
    static String uniqueEntryName(String name, Set<String> usedEntryNames) {
        String candidate = name;
        for (int i = 2; !usedEntryNames.add(candidate); i++) {
            candidate = withSuffix(name, " (" + i + ")");
        }
        return candidate;
    }

    private static String withSuffix(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name + suffix : name.substring(0, dot) + suffix + name.substring(dot);
    }
}
