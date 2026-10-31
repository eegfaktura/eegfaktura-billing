package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.logging.LogText;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class BillingDocumentMailService {

    /** Run protocol note when the EEG's mail text could not be used (concept: default + notice). */
    static final String TEMPLATE_FALLBACK_NOTE = "HINWEIS: Die angepasste E-Mail-Vorlage konnte nicht verwendet "
            + "werden, es wurde der Standardtext versendet. ";
    /** Run protocol note when the mail run ended on an exception instead of a result. */
    static final String ABORTED_NOTE = "ABBRUCH: Der Mailversand wurde wegen eines Fehlers beendet. ";

    static final String STATUS_IN_PROGRESS = "IN PROGRESS";
    /** At least one mail went out, or there was nothing to send; failures are in the run protocol. */
    static final String STATUS_SENT = "SENT";
    /** No mail went out although there were documents. Like every status it blocks a further mail run. */
    static final String STATUS_FAILED = "FAILED";

    private final EmailService emailService;
    private final BillingDocumentFileRepository billingDocumentFileRepository;
    private final FileDataRepository fileDataRepository;
    private final AppProperties appProperties;
    private final BillingRunRepository billingRunRepository;
    private final BillingDocumentRepository billingDocumentRepository;
    private final MailTemplateService mailTemplateService;

    public BillingDocumentMailService(final EmailService emailservice,
                                      final BillingDocumentFileRepository billingDocumentFileRepository,
                                      final FileDataRepository fileDataRepository,
                                      final AppProperties appProperties,
                                      BillingRunRepository billingRunRepository,
                                      BillingDocumentRepository billingDocumentRepository,
                                      MailTemplateService mailTemplateService) {
        this.emailService = emailservice;
        this.billingDocumentFileRepository = billingDocumentFileRepository;
        this.fileDataRepository = fileDataRepository;
        this.appProperties = appProperties;
        this.billingRunRepository = billingRunRepository;
        this.billingDocumentRepository = billingDocumentRepository;
        this.mailTemplateService = mailTemplateService;
    }

    /**
     * Sends one document. Subject and text come from the EEG's mail text (or the default), with the
     * placeholder values escaped; the document PDF is always attached. Returns true if the stored
     * text could not be rendered for this document and the default was sent instead.
     */
    private boolean createAndSendMail(BillingDocument billingDocument, MailTemplateService.Prepared prepared,
                                      List<String> rejected) {

        try {
            List<BillingDocumentFile> billingDocumentFiles = billingDocumentFileRepository.findByBillingDocumentId(
                    billingDocument.getId()
            );
            if (billingDocumentFiles.isEmpty()) {
                throw new RuntimeException("BillingDocumentFile not found for billingDocumentId ="
                        +billingDocument.getId());
            }
            FileData fileData = fileDataRepository
                    .findById(billingDocumentFiles.get(0).getFileDataId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            billingDocumentFiles.get(0).getFileDataId().toString()));

            Map<String, byte[]> attachments = new HashMap<>();
            attachments.put(fileData.getName(), fileData.getData());

            Map<String, String> values = MailTemplateService.values(billingDocument);
            MailTemplateService.RenderedMail mail;
            boolean fellBack = false;
            try {
                mail = mailTemplateService.render(prepared, values, true);
            } catch (RuntimeException e) {
                if (!prepared.customized()) {
                    throw e;
                }
                // a member never gets a broken or no mail because of an EEG text
                log.warn("Stored mail text not renderable for document {}, sending the default: {}",
                        billingDocument.getId(), e.getMessage());
                mail = mailTemplateService.renderDefault(values);
                fellBack = true;
            }

            String to = billingDocument.getRecipientEmail();
            String cc = billingDocument.getIssuerMail();
            String from = appProperties.getNoReplyTo();

            // Rejected parts are NOT a failed send — the mail went out to
            // the valid recipients. Reporting them as FEHLER would trigger
            // manual re-sends (duplicate invoices); they surface as a
            // warning in the run protocol instead.
            rejected.addAll(emailService.sendEmail(
                    from, to, cc, mail.subject(), mail.html(), mail.inlineImages(), attachments));
            return fellBack;
        } catch (Exception e) {
            // no address and no cause message here: SMTP errors name the recipient (never-log list); the cause stays
            throw new RuntimeException("Mailversand fuer Beleg " + billingDocument.getId() + " fehlgeschlagen", e);
        }

    }

    /**
     * One ERROR per failed mail with the root cause's type. An SMTP error names the recipient: its message is logged
     * masked and without the stack trace (which repeats it); any other failure (NPE, Jasper, ...) keeps the stack.
     */
    private static void logSendFailure(BillingDocument billingDocument, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof MessagingException || cause instanceof MailException) {
            log.atError().addKeyValue("error.type", cause.getClass().getName())
                    .log("Failed to send mail (billingDocumentId={}): {}", billingDocument.getId(),
                            LogText.withoutEmails(cause.getMessage()));
        } else {
            log.error("Failed to send mail (billingDocumentId={})", billingDocument.getId(), cause);
        }
    }

    public String sendAllBillingDocuments(UUID billingRunId) {
        BillingRun billingRun = billingRunRepository
                .findById(billingRunId)
                        .orElseThrow(() -> new EntityNotFoundException(billingRunId.toString()));

        var mailStatus = billingRun.getMailStatus();
        if (mailStatus!=null && !mailStatus.isEmpty()) {
            var errorText = "Mailversand fuer "+billingRun.getClearingPeriodIdentifier()+" ("
                    +billingRun.getId()+") nicht moeglich. Status = "+mailStatus;
            throw new RuntimeException(errorText);
        }
        // two simultaneous calls both pass the check above: only the claim has one winner (known-errors #18)
        if (billingRunRepository.claimMailRun(billingRunId, STATUS_IN_PROGRESS) == 0) {
            throw new RuntimeException("Mailversand fuer "+billingRun.getClearingPeriodIdentifier()+" ("
                    +billingRun.getId()+") nicht moeglich. Ein anderer Versand hat ihn gerade begonnen");
        }

        StringBuilder sendProtocolStringBuilder = new StringBuilder();
        sendProtocolStringBuilder.append("Send mail start at ")
                .append(OffsetDateTime.now())
                .append(": ");
        int sent = 0;
        try {
            List<BillingDocument> billingDocuments = billingDocumentRepository.findByBillingRunId(billingRunId);
            // the EEG's mail text is loaded once per run (also: a change during the run does not apply)
            MailTemplateService.Prepared prepared = mailTemplateService.prepareForSend(billingRun.getTenantId());
            boolean templateFallbackNoted = false;
            if (prepared.failure() != null) {
                sendProtocolStringBuilder.append(TEMPLATE_FALLBACK_NOTE);
                templateFallbackNoted = true;
            }
            for(BillingDocument billingDocument : billingDocuments) {
                try {
                    List<String> rejected = new ArrayList<>();
                    if (createAndSendMail(billingDocument, prepared, rejected) && !templateFallbackNoted) {
                        sendProtocolStringBuilder.append(TEMPLATE_FALLBACK_NOTE);
                        templateFallbackNoted = true;
                    }
                    sent++;
                    sendProtocolStringBuilder.append(billingDocument.getRecipientEmail()).append(" OK");
                    if (!rejected.isEmpty()) {
                        // Delivered, but some address parts (e.g. an invalid cc)
                        // were dropped — warn without flagging the send as failed.
                        sendProtocolStringBuilder.append(" (nicht zugestellt an ungültige Adresse: ")
                                .append(String.join(";", rejected)).append(")");
                    }
                    sendProtocolStringBuilder.append(",");
                } catch (Exception e) {
                    logSendFailure(billingDocument, e);
                    // Identify the member behind the failed address — with a
                    // blank/garbage address the bare e-mail ("  FEHLER") is
                    // impossible to attribute for the tenant admin.
                    sendProtocolStringBuilder.append(billingDocument.getRecipientEmail())
                            .append(" (MitgliedsNr ").append(Objects.toString(billingDocument.getRecipientParticipantNumber(), "-"))
                            .append(", ").append(Objects.toString(billingDocument.getRecipientName(), "-")).append(")")
                            .append(" FEHLER,");
                }
            }
            String status = sent == 0 && !billingDocuments.isEmpty() ? STATUS_FAILED : STATUS_SENT;
            return finishMailRun(billingRun, status, sendProtocolStringBuilder);
        } catch (RuntimeException e) {
            sendProtocolStringBuilder.append(ABORTED_NOTE);
            try {
                finishMailRun(billingRun, sent == 0 ? STATUS_FAILED : STATUS_SENT, sendProtocolStringBuilder);
            } catch (RuntimeException statusNotSaved) {
                e.addSuppressed(statusNotSaved);
            }
            throw e;
        }
    }

    /** Writes the final status and the protocol; the run never ends "IN PROGRESS" while the database answers. */
    private String finishMailRun(BillingRun billingRun, String status, StringBuilder sendProtocolStringBuilder) {
        billingRun.setMailStatus(status);
        billingRun.setMailStatusDateTime(LocalDateTime.now());
        sendProtocolStringBuilder.append("Send mail finished at ").append(OffsetDateTime.now());
        var sendMailProtocol = sendProtocolStringBuilder.toString();
        billingRun.setSendMailProtocol(sendMailProtocol);
        billingRunRepository.save(billingRun);
        return sendMailProtocol;
    }

    //@TODO: Weitere Versandoption waere sendBillingDocumentsFromBillingRun (dann könnte man Sammelmails senden)
    //    Map<String[], List<BillingDocumentDTO>> participantNumbersMap = billingDocumentDTOs.stream()
    //            .collect(Collectors.groupingBy(billingDocumentDTO -> new String[] {
    //                    billingDocumentDTO.getRecipientParticipantNumber(),
    //                    billingDocumentDTO.getRecipientEmail()}));
}
