package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.transaction.Transactional;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.vfeeg.eegfaktura.billing.util.EmailAddressUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional

public class EmailService {

    private final JavaMailSender emailSender;

    public EmailService(final JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    /**
     * Sends the mail to all valid recipients and returns the rejected
     * (invalid) address parts so the caller can surface them — instead
     * of handing raw strings to the mail parser ("Illegal address").
     * Addresses are normalized per ';'-part (unicode strip incl. NBSP)
     * and validated against the shared suite-wide rule; the normalized
     * values are what actually gets sent. No valid "to" at all is an
     * error.
     */
    public List<String> sendEmail(
            String from , String to, String cc, String subject, String htmlBody,
            Map<String, byte[]> attachments) throws MessagingException {

        List<String> rejected = new ArrayList<>();

        List<String> toParts = EmailAddressUtil.normalize(to);
        List<String> validTo = toParts.stream().filter(EmailAddressUtil::isValid).toList();
        rejected.addAll(toParts.stream().filter(p -> !EmailAddressUtil.isValid(p)).toList());
        if (validTo.isEmpty()) {
            throw new MessagingException("invalid email (" + to + ")");
        }

        List<String> ccParts = EmailAddressUtil.normalize(cc);
        List<String> validCc = ccParts.stream().filter(EmailAddressUtil::isValid).toList();
        rejected.addAll(ccParts.stream().filter(p -> !EmailAddressUtil.isValid(p)).toList());

        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(validTo.toArray(new String[0]));
        if (!validCc.isEmpty()) {
            helper.setCc(validCc.toArray(new String[0]));
            // Remove replyTo and return Path header because it might increase SPAM score
            // helper.setReplyTo(ccArray[0]);
            // message.setHeader("return-path", ccArray[0]);
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

        return rejected;
    }
}
