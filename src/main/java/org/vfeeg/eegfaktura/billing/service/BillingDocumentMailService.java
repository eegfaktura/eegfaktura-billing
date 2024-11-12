package org.vfeeg.eegfaktura.billing.service;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentFile;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.AppProperties;
import org.vfeeg.eegfaktura.billing.util.ClearingPeriodIdentifierTool;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class BillingDocumentMailService {

    public static final String ATTACHMENT_LOGO_NAME = "attachment-logo.png";
    private Template defaultTemplate;

    private byte[] defaultLogo;
    private final EmailService emailService;
    private final BillingDocumentFileRepository billingDocumentFileRepository;
    private final FileDataRepository fileDataRepository;
    private final AppProperties appProperties;
    private final BillingRunRepository billingRunRepository;
    private final BillingDocumentRepository billingDocumentRepository;

    public BillingDocumentMailService(final EmailService emailservice,
                                      final BillingDocumentFileRepository billingDocumentFileRepository,
                                      final FileDataRepository fileDataRepository,
                                      final AppProperties appProperties,
                                      BillingRunRepository billingRunRepository,
                                      BillingDocumentRepository billingDocumentRepository) {
        this.emailService = emailservice;
        this.billingDocumentFileRepository = billingDocumentFileRepository;
        this.fileDataRepository = fileDataRepository;
        this.appProperties = appProperties;
        this.billingRunRepository = billingRunRepository;
        this.billingDocumentRepository = billingDocumentRepository;
    }

    private byte[] getDefaultLogo()  {
        if (defaultLogo==null) {
            try {
                defaultLogo = new ClassPathResource("eeg-faktura-logo.png").getContentAsByteArray();
            } catch (Exception e) {
                defaultLogo = null;
            }
        }
        return defaultLogo;
    }

    private Template getDefaultTemplate() {
        if (defaultTemplate == null) {
            try {
                Configuration configuration = new Configuration(Configuration.VERSION_2_3_27);
                TemplateLoader templateLoader = new ClassTemplateLoader(this.getClass(), "/");
                configuration.setTemplateLoader(templateLoader);
                FreeMarkerConfigurer freeMarkerConfigurer = new FreeMarkerConfigurer();
                freeMarkerConfigurer.setConfiguration(configuration);
                defaultTemplate = freeMarkerConfigurer.getConfiguration()
                        .getTemplate("BillingEmailDefaultTemplate.ftl");
            } catch (Exception e) {
                throw new RuntimeException("Failed to create and save PDF for BillingDocument due to "+e.getMessage(), e);
            }
        }
        return defaultTemplate;
    }

    private void createAndSendMail(BillingDocument billingDocument) {

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

            Template freemarkerTemplate = getDefaultTemplate();
            Map<String, Object> templateModel = new HashMap<>();
            templateModel.put("recipientName", billingDocument.getRecipientName());
            templateModel.put("billingDocumentType", BillingDocument.getDocumentTypeName(
                    billingDocument.getBillingDocumentType()));
            templateModel.put("clearingPeriodIdentifier", ClearingPeriodIdentifierTool.asText(billingDocument.getClearingPeriodIdentifier()));
            templateModel.put("issuerName", billingDocument.getIssuerName());
            templateModel.put("footerText", billingDocument.getFooterText());

            byte[] logo = getDefaultLogo();
            if (logo !=null) {
                attachments.put(ATTACHMENT_LOGO_NAME, getDefaultLogo());
                templateModel.put("logoName", ATTACHMENT_LOGO_NAME);
            }

            String to = billingDocument.getRecipientEmail();
            String cc = billingDocument.getIssuerMail();
            String from = appProperties.getNoReplyTo();
            String subject = BillingDocument.getDocumentTypeName(
                    billingDocument.getBillingDocumentType()) + " " +billingDocument.getClearingPeriodIdentifier();
            String htmlBody = FreeMarkerTemplateUtils.processTemplateIntoString(freemarkerTemplate, templateModel);

            emailService.sendEmail(
                    from,to, cc, subject, htmlBody, attachments);
        } catch (Exception e) {
            throw new RuntimeException("Mailversand ("+billingDocument.getRecipientEmail()+") fehlgeschlagen aufgrund: "+e.getMessage(), e);
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
        } else {
            billingRun.setMailStatus("IN PROGRESS");
            billingRunRepository.saveAndFlush(billingRun);
        }

        //@TODO: Für EEG passendes Logo holen und verwenden (aus BillingConfig)
        List<BillingDocument> billingDocuments = billingDocumentRepository.findByBillingRunId(billingRunId);
        StringBuilder sendProtocolStringBuilder = new StringBuilder();
        sendProtocolStringBuilder.append("Send mail start at ")
                .append(OffsetDateTime.now())
                .append(": ");
        for(BillingDocument billingDocument : billingDocuments) {
            try {
                createAndSendMail(billingDocument);
                sendProtocolStringBuilder.append(billingDocument.getRecipientEmail()).append(" OK,");
            } catch (Exception e) {
                log.error("Failed to send mail: {}", e.getMessage(), e);
                sendProtocolStringBuilder.append(billingDocument.getRecipientEmail()).append(" FEHLER,");
            }
        }
        billingRun.setMailStatus("SENT");
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
