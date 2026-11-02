package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The real way through JasperReports: compile the template, fill it, export the PDF. The template compiles lazily, so
 * neither the start of the service nor another test would notice a stack on which it no longer works.
 */
class BillingPdfRenderTests {

    @Test
    void theDefaultTemplateBecomesAPdf() throws Exception {
        FileDataRepository files = mock(FileDataRepository.class);
        BillingDocumentFileRepository documentFiles = mock(BillingDocumentFileRepository.class);
        AtomicReference<byte[]> pdf = new AtomicReference<>();
        when(files.save(any())).thenAnswer(call -> {
            FileData file = call.getArgument(0);
            pdf.set(file.getData());
            return file;
        });
        when(documentFiles.save(any())).thenAnswer(call -> (BillingDocumentFile) call.getArgument(0));

        BillingDocument document = new BillingDocument();
        document.setBillingDocumentType(BillingDocumentType.INVOICE);
        document.setDocumentDate(LocalDate.of(2026, 10, 31));
        document.setDocumentNumber("RE-1");
        document.setClearingPeriodIdentifier("YH-2026");
        document.setIssuerName("Test EEG");
        document.setRecipientName("Grüße & Söhne");
        document.setRecipientBankIban("AT611904300234573201");
        document.setVat1Percent(new BigDecimal("20"));
        document.setVat1SumInEuro(new BigDecimal("2.00"));
        document.setGrossAmountInEuro(new BigDecimal("12.00"));
        document.setNetAmountInEuro(new BigDecimal("10.00"));
        document.setTenantId("RC100001");

        BillingDocumentItem item = new BillingDocumentItem();
        item.setMeteringPointId("AT0030000000000000000000000000001");
        item.setText("Energie");
        item.setNetValue(new BigDecimal("10.00"));

        new BillingPdfService(documentFiles, files).createAndSavePDF(document, List.of(item), Map.of(), null, null, false);

        assertThat(new String(pdf.get(), 0, 5, StandardCharsets.ISO_8859_1), is("%PDF-"));
        assertThat(pdf.get().length, greaterThan(1000));
    }
}
