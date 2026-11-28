package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

/**
 * {@code mailTemplateFallback} of a run: true when its mails went out (SENT) and the protocol of that send holds the
 * note that the EEG's mail text could not be used (platform#280).
 */
@ExtendWith(MockitoExtension.class)
class MailTemplateFallbackTests {

    @Mock private BillingRunRepository billingRunRepository;
    @Mock private BillingDocumentRepository billingDocumentRepository;
    @Mock private BillingDocumentItemRepository billingDocumentItemRepository;
    @Mock private BillingDocumentFileRepository billingDocumentFileRepository;
    @Mock private FileDataRepository fileDataRepository;

    private BillingRunService service;

    @BeforeEach
    void setUp() {
        service = new BillingRunService(billingRunRepository, billingDocumentRepository,
                billingDocumentItemRepository, billingDocumentFileRepository, fileDataRepository);
    }

    // the wording of protocols stored by earlier versions (since 22.10.2026), written out on purpose
    private static final String STORED_NOTE = "HINWEIS: Die angepasste E-Mail-Vorlage konnte nicht verwendet werden, "
            + "es wurde der Standardtext versendet. ";

    @Test
    void sentRunWithTheFallbackNote() {
        assertThat(fallback("SENT", "Send mail start at 2026-11-28T10:00+01:00: " + STORED_NOTE
                + "a@example.org OK, "), is(true));
    }

    @Test
    void sentRunWithoutTheFallbackNote() {
        assertThat(fallback("SENT", "Send mail start at 2026-11-28T10:00+01:00: a@example.org OK, "), is(false));
    }

    @Test
    void failedOrRunningSendIsNoFallback() {
        assertThat(fallback("FAILED", "Send mail start at …: " + STORED_NOTE + "a@example.org FEHLER,"), is(false));
        assertThat(fallback("IN PROGRESS", "Send mail start at …: " + STORED_NOTE), is(false));
    }

    @Test
    void runWithoutMailRun() {
        assertThat(fallback(null, null), is(false));
    }

    private boolean fallback(String mailStatus, String protocol) {
        BillingRun run = new BillingRun();
        UUID id = UUID.randomUUID();
        run.setId(id);
        run.setMailStatus(mailStatus);
        run.setSendMailProtocol(protocol);
        when(billingRunRepository.findById(id)).thenReturn(Optional.of(run));
        return service.get(id).isMailTemplateFallback();
    }
}
