package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.transaction.Transactional;
import org.hibernate.validator.internal.util.StringHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.Map;

@Service
@Transactional

public class EmailService {

    private final JavaMailSender emailSender;

    public EmailService(final JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void sendEmail(
            String from , String to, String cc, String subject, String htmlBody,
            Map<String, byte[]> attachments) throws MessagingException {

        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to.split(";"));
        if (!StringHelper.isNullOrEmptyString(cc)) {
            helper.setCc(cc.split(";"));
        }
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        for (Map.Entry<String, byte[]> attachment : attachments.entrySet()) {
            if (attachment.getKey().contains(".png")) {
                helper.addInline(attachment.getKey(), new ByteArrayDataSource(attachment.getValue(),
                        MimeTypeUtils.IMAGE_PNG_VALUE));
            } else {
                helper.addAttachment(attachment.getKey(), new ByteArrayDataSource(attachment.getValue(),
                        "application/pdf"));
            }
        }
        emailSender.send(message);

    }
}
