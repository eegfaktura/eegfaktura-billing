package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentArchiveService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentXlsxService;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.CREDIT_NOTE;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.CREDIT_NOTE_RC;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INFO;
import static org.vfeeg.eegfaktura.billing.domain.BillingDocumentType.INVOICE;
import static org.vfeeg.eegfaktura.billing.support.DocumentReaders.columnSum;
import static org.vfeeg.eegfaktura.billing.support.DocumentReaders.dataRows;
import static org.vfeeg.eegfaktura.billing.support.DocumentReaders.header;
import static org.vfeeg.eegfaktura.billing.support.DocumentReaders.pdfText;
import static org.vfeeg.eegfaktura.billing.support.DocumentReaders.zipEntries;

/** Scenario S2 (document types) and the printed outputs of S1 and S2: PDF text, XLSX sums, ZIP archive. */
class BillingScenarioOutputTests extends BillingScenarioBase {

    /** XLSX columns of sheet "Liste" and "Details" (pinned by their header text). */
    private static final int LIST_GROSS = 23;
    private static final int DETAILS_GROSS = 28;

    @Autowired
    BillingDocumentXlsxService xlsxService;
    @Autowired
    BillingDocumentArchiveService archiveService;

    /** S1 world: two consumers at 20 % VAT (gross 18.00 and 36.06, see {@code BillingScenarioVatTests}). */
    private BillingRunFixture s1World() {
        return insert(world()
                .master(consumer(1, "C1").tariffVat("20").participantFee("5", "20"), "100")
                .master(consumer(2, "C2").tariffVat("20").participantFee("5", "20"), "250.50"), false);
    }

    /**
     * S2 world: consumer 1 (100 kWh × 10 ct = 10.00), producer 2 with a VAT id (1000 kWh × 8 ct = 80.00 + 20 %
     * = 96.00), producer 3 without a VAT id (500 kWh × 8 ct = 40.00, no VAT).
     */
    private BillingRunFixture s2World(boolean creditNotesForAllProducers) {
        return insert(world()
                .master(consumer(1, "C1"), "100")
                .master(producer(2, "P2").column("participant_vat_id", "ATU12345678").tariffVat("20"), "1000")
                .master(producer(3, "P3"), "500"), creditNotesForAllProducers);
    }

    @Test
    void s01_pdfShowsRecipientNumberVatAndGross() {
        DoBillingResults results = bill(s1World(), false);
        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());

        BillingDocumentDTO p1 = document(docs, 1, INVOICE);
        String text = pdfText(pdf(p1.getId()));
        assertThat(text, allOf(containsString("RECHNUNG"), containsString("Vorname1 Nachname1"),
                containsString("Nummer: " + p1.getDocumentNumber()), containsString("USt 20,00 %"),
                containsString("3,00"), containsString("18,00"), containsString("Mitgliedsgebühr")));
        String text2 = pdfText(pdf(document(docs, 2, INVOICE).getId()));
        assertThat(text2, allOf(containsString("Vorname2 Nachname2"), containsString("6,01"),
                containsString("36,06")));
    }

    @Test
    void s01_xlsxListsEveryDocumentAndItemWithTheirSums() throws IOException {
        DoBillingResults results = bill(s1World(), false);
        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));

        byte[] xlsx = xlsxService.createXlsx(results.getBillingRunId());

        assertThat(header(xlsx, "Liste", LIST_GROSS), is("Rechnungsbetrag Brutto"));
        assertThat(header(xlsx, "Details", DETAILS_GROSS), is("Pos. Bruttobetrag"));
        assertThat(dataRows(xlsx, "Liste"), is(2));
        assertThat(columnSum(xlsx, "Liste", LIST_GROSS), comparesEqualTo(new BigDecimal("54.06")));
        assertThat(dataRows(xlsx, "Details"), is(4));
        assertThat(columnSum(xlsx, "Details", DETAILS_GROSS), comparesEqualTo(new BigDecimal("54.06")));
    }

    /** S2 — the community sends credit notes to all producers: reverse-charge credit note for the VAT id. */
    @Test
    void s02_producerWithVatIdGetsReverseChargeCreditNote() throws IOException {
        DoBillingResults results = bill(s2World(true), false);

        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs, hasSize(3));
        assertAmounts(document(docs, 1, INVOICE), "10.00", null, null, null, null, "10.00");
        BillingDocumentDTO rc = document(docs, 2, CREDIT_NOTE_RC);
        assertAmounts(rc, "80.00", "20", "16.00", null, null, "96.00");
        BillingDocumentDTO plain = document(docs, 3, CREDIT_NOTE);
        assertAmounts(plain, "40.00", null, null, null, null, "40.00");
        assertThat(document(docs, 1, INVOICE).getDocumentNumber(), is("TRECH202400042"));
        assertThat(List.of(rc.getDocumentNumber(), plain.getDocumentNumber()),
                containsInAnyOrder("TGUT202400073", "TGUT202400074"));
        assertThat(documentNumbers(), hasSize(3));

        assertThat(pdfText(pdf(rc.getId())), allOf(containsString("GUTSCHRIFT"),
                containsString("Nummer: " + rc.getDocumentNumber()), containsString("Vorname2 Nachname2"),
                containsString("USt 20,00 %"), containsString("16,00"), containsString("96,00")));
        byte[] xlsx = xlsxService.createXlsx(results.getBillingRunId());
        assertThat(dataRows(xlsx, "Liste"), is(3));
        assertThat(columnSum(xlsx, "Liste", LIST_GROSS), comparesEqualTo(new BigDecimal("146.00")));
        assertThat(dataRows(xlsx, "Details"), is(3));
        assertThat(columnSum(xlsx, "Details", DETAILS_GROSS), comparesEqualTo(new BigDecimal("146.00")));
    }

    /** S2 — credit notes only for producers without a VAT id: the VAT id gets an unnumbered information. */
    @Test
    void s02_producerWithVatIdGetsInformationWhenNotAllProducersGetCreditNotes() {
        DoBillingResults results = bill(s2World(false), false);

        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs, hasSize(3));
        BillingDocumentDTO info = document(docs, 2, INFO);
        assertAmounts(info, "80.00", "20", "16.00", null, null, "96.00");
        assertThat(info.getDocumentNumber(), is("-"));
        assertThat(document(docs, 3, CREDIT_NOTE).getDocumentNumber(), is("TGUT202400073"));
        assertThat(documentNumbers(), containsInAnyOrder("TRECH202400042", "TGUT202400073"));
        assertThat(pdfText(pdf(info.getId())), allOf(containsString("INFORMATION"), containsString("Nummer: -"),
                containsString("96,00")));
        assertThat(pdfText(pdf(document(docs, 3, CREDIT_NOTE).getId())), allOf(containsString("GUTSCHRIFT"),
                containsString("Nummer: TGUT202400073"), containsString("40,00")));
    }

    /** S2 as preview: drafts without numbers, the PDF says so. */
    @Test
    void s02_previewDocumentsAreUnnumberedDrafts() {
        DoBillingResults results = bill(s2World(true), true);

        assertThat(results.getAbstractText(), is(RESULT_PREVIEW_OK));
        List<BillingDocumentDTO> docs = documents(results.getBillingRunId());
        assertThat(docs.stream().map(BillingDocumentDTO::getDocumentNumber).toList(),
                everyItem(nullValue(String.class)));
        assertThat(pdfText(pdf(document(docs, 1, INVOICE).getId())), containsString("E N T W U R F"));
        assertThat(documentNumbers(), hasSize(0));
    }

    /** The archive of a run holds one PDF per document (was a {@code @TODO} in BillingIntegrationTests). */
    @Test
    void archiveHoldsOnePdfPerDocumentOfTheRun() throws IOException {
        DoBillingResults results = bill(s2World(true), false);
        assertThat(results.getAbstractText(), is(RESULT_FINAL_OK));

        List<String> names = new ArrayList<>();
        List<byte[]> contents = zipEntries(archiveService.createArchive(results.getBillingRunId()), names);

        assertThat(names, hasSize(3));
        assertThat(names, everyItem(allOf(containsString(PERIOD), containsString(".pdf"))));
        assertThat(contents.stream().map(c -> new String(c, 0, 5, StandardCharsets.US_ASCII))
                .toList(), everyItem(startsWith("%PDF-")));
    }
}
