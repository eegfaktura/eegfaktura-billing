package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.CREDIT_NOTE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INFO;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;
import static org.vfeeg.eegfaktura.billing.support.DocumentReaders.zipEntries;

/** Entry names in the ZIP archive of a run (platform#221): no hash prefix, member number for preview/INFO. */
@ExtendWith(MockitoExtension.class)
class BillingDocumentArchiveServiceTests {

    private static final UUID RUN_ID = UUID.randomUUID();

    @Mock private BillingDocumentFileRepository billingDocumentFileRepository;
    @Mock private FileDataRepository fileDataRepository;

    /** Archives the files; each file's content is its own id, so the test can map entries back to files. */
    private List<String> archive(BillingDocumentFile... files) throws IOException {
        return new ArrayList<>(archiveWithContents(files).keySet());
    }

    /** Entry name to content (the file data id), in ZIP order. */
    private Map<String, String> archiveWithContents(BillingDocumentFile... files) throws IOException {
        doReturn(List.of(files)).when(billingDocumentFileRepository).findByBillingRunId(RUN_ID);
        doAnswer(call -> {
            FileData fileData = new FileData();
            fileData.setData(call.getArgument(0).toString().getBytes(StandardCharsets.US_ASCII));
            return Optional.of(fileData);
        }).when(fileDataRepository).findById(any());
        List<String> names = new ArrayList<>();
        List<byte[]> contents = zipEntries(new BillingDocumentArchiveService(billingDocumentFileRepository,
                fileDataRepository).createArchive(RUN_ID), names);
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            entries.put(names.get(i), new String(contents.get(i), StandardCharsets.US_ASCII));
        }
        // every document exactly once, no entry name twice
        assertThat(entries.size(), is(names.size()));
        assertThat(entries.values(),
                containsInAnyOrder(Stream.of(files).map(f -> f.getFileDataId().toString()).toArray()));
        return entries;
    }

    private static BillingDocumentFile file(String name, BillingDocumentType type, String documentNumber,
                                            String participantNumber) {
        BillingDocument document = new BillingDocument();
        document.setBillingDocumentType(type);
        document.setDocumentNumber(documentNumber);
        document.setRecipientParticipantNumber(participantNumber);
        BillingDocumentFile file = new BillingDocumentFile();
        file.setId(UUID.randomUUID());
        file.setFileDataId(UUID.randomUUID());
        file.setName(name);
        file.setBillingDocument(document);
        return file;
    }

    @Test
    void invoiceAndCreditNoteKeepTheirFileNameWithoutPrefix() throws IOException {
        assertThat(archive(
                        file("Abr_YM-2025-6_Rechnung_RG202500304.pdf", INVOICE, "RG202500304", "007"),
                        file("Abr_YM-2025-6_Gutschrift_GS202500012.pdf", CREDIT_NOTE, "GS202500012", "008")),
                contains("Abr_YM-2025-6_Gutschrift_GS202500012.pdf", "Abr_YM-2025-6_Rechnung_RG202500304.pdf"));
    }

    @Test
    void previewsGetTheMemberNumber() throws IOException {
        assertThat(archive(
                        file("Abr_YM-2025-6_Rechnung_Vorschau.pdf", INVOICE, null, "12"),
                        file("Abr_YM-2025-6_Rechnung_Vorschau.pdf", INVOICE, "", "11")),
                contains("Abr_YM-2025-6_Rechnung_Vorschau_11.pdf", "Abr_YM-2025-6_Rechnung_Vorschau_12.pdf"));
    }

    @Test
    void infoDocumentsGetTheMemberNumber() throws IOException {
        assertThat(archive(
                        file("Abr_YM-2025-6_Information_20250701.pdf", INFO, "-", "21"),
                        file("Abr_YM-2025-6_Information_20250701.pdf", INFO, "-", "22")),
                contains("Abr_YM-2025-6_Information_20250701_21.pdf", "Abr_YM-2025-6_Information_20250701_22.pdf"));
    }

    @Test
    void remainingDuplicatesGetACounterInAStableOrder() throws IOException {
        BillingDocumentFile first = file("Abr_YM-2025-6_Rechnung_Vorschau.pdf", INVOICE, null, "5");
        BillingDocumentFile second = file("Abr_YM-2025-6_Rechnung_Vorschau.pdf", INVOICE, null, "5");
        BillingDocumentFile third = file("Abr_YM-2025-6_Rechnung_Vorschau.pdf", INVOICE, null, "5");
        List<BillingDocumentFile> byId = new ArrayList<>(List.of(first, second, third));
        byId.sort((a, b) -> a.getId().compareTo(b.getId()));

        Map<String, String> entries = archiveWithContents(third, first, second);

        assertThat(new ArrayList<>(entries.keySet()), contains("Abr_YM-2025-6_Rechnung_Vorschau_5.pdf",
                "Abr_YM-2025-6_Rechnung_Vorschau_5_2.pdf", "Abr_YM-2025-6_Rechnung_Vorschau_5_3.pdf"));
        // ties are broken by the file id
        assertThat(new ArrayList<>(entries.values()),
                is(byId.stream().map(f -> f.getFileDataId().toString()).toList()));
        // the same files in another order give the same ZIP
        assertThat(archiveWithContents(second, third, first), is(entries));
    }

    @Test
    void counterNeverReusesANameThatIsAlreadyTaken() throws IOException {
        // sorted by name: the two "…Vorschau.pdf" come first, so the second one takes "_2" and the file that
        // is really called "…Vorschau_2.pdf" becomes "…Vorschau_2_2.pdf"
        assertThat(archive(
                        file("Abr_X_Rechnung_Vorschau_2.pdf", INVOICE, null, null),
                        file("Abr_X_Rechnung_Vorschau.pdf", INVOICE, null, null),
                        file("Abr_X_Rechnung_Vorschau.pdf", INVOICE, null, null)),
                contains("Abr_X_Rechnung_Vorschau.pdf", "Abr_X_Rechnung_Vorschau_2.pdf",
                        "Abr_X_Rechnung_Vorschau_2_2.pdf"));
    }

    @Test
    void missingMemberNumberAddsNoSuffix() throws IOException {
        assertThat(archive(
                        file("Abr_YM-2025-6_Rechnung_Vorschau.pdf", INVOICE, null, null),
                        file("Abr_YM-2025-6_Information_20250701.pdf", INFO, "-", "  ")),
                contains("Abr_YM-2025-6_Information_20250701.pdf", "Abr_YM-2025-6_Rechnung_Vorschau.pdf"));
    }

    @Test
    void memberNumberIsSanitisedForFileNames() throws IOException {
        assertThat(archive(file("Abr_YM-2025-6_Rechnung_Vorschau.pdf", INVOICE, null, "a/b\\c:d*e?f\"g<h>i|j")),
                contains("Abr_YM-2025-6_Rechnung_Vorschau_a_b_c_d_e_f_g_h_i_j.pdf"));
    }
}
