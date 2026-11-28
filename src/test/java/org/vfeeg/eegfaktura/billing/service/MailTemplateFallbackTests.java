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
 * {@code mailTemplateFallback} of a run: true when the protocol of its last mail run holds the note that the
 * EEG's mail text could not be used (platform#280).
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

    @Test
    void protocolWithTheFallbackNote() {
        assertThat(fallback("Send mail start at 2026-11-28T10:00+01:00: "
                + BillingDocumentMailService.TEMPLATE_FALLBACK_NOTE + "a@example.org OK, "), is(true));
    }

    @Test
    void protocolWithoutTheFallbackNote() {
        assertThat(fallback("Send mail start at 2026-11-28T10:00+01:00: a@example.org OK, "), is(false));
    }

    @Test
    void runWithoutMailRun() {
        assertThat(fallback(null), is(false));
    }

    private boolean fallback(String protocol) {
        BillingRun run = new BillingRun();
        UUID id = UUID.randomUUID();
        run.setId(id);
        run.setSendMailProtocol(protocol);
        when(billingRunRepository.findById(id)).thenReturn(Optional.of(run));
        return service.get(id).isMailTemplateFallback();
    }
}
