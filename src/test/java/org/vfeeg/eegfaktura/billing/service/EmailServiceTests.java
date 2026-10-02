package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Mocked {@link JavaMailSender}: proves the built message, not delivery (real SMTP is M5, GreenMail). */
@ExtendWith(MockitoExtension.class)
class EmailServiceTests {

    private static final String FROM = "no-reply@eegfaktura.at";

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        lenient().when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendsToValidRecipientsAndReturnsNoRejects() throws Exception {
        List<String> rejected = emailService.sendEmail(FROM, "anna@example.at", "office@eeg.at",
                "Rechnung Abr_YQ-2024-3", "<p>Hallo</p>", Map.of());

        MimeMessage sent = sentMessage();
        assertThat(rejected, is(empty()));
        assertThat(addresses(sent.getFrom()), contains(FROM));
        assertThat(addresses(sent.getRecipients(Message.RecipientType.TO)), contains("anna@example.at"));
        assertThat(addresses(sent.getRecipients(Message.RecipientType.CC)), contains("office@eeg.at"));
        assertThat(sent.getSubject(), is("Rechnung Abr_YQ-2024-3"));
    }

    @Test
    void invalidAndBlankPartsAreRejectedAndNotSent() throws Exception {
        List<String> rejected = emailService.sendEmail(FROM, " anna@example.at ; kaputt ;; bert@example.at",
                "office@eeg.at;no-at-sign", "S", "<p/>", Map.of());

        MimeMessage sent = sentMessage();
        assertThat(rejected, contains("kaputt", "no-at-sign"));
        assertThat(addresses(sent.getRecipients(Message.RecipientType.TO)),
                contains("anna@example.at", "bert@example.at"));
        assertThat(addresses(sent.getRecipients(Message.RecipientType.CC)), contains("office@eeg.at"));
    }

    @Test
    void noValidCcMeansNoCcHeader() throws Exception {
        List<String> rejected = emailService.sendEmail(FROM, "anna@example.at", "kaputt", "S", "<p/>", Map.of());

        assertThat(rejected, contains("kaputt"));
        assertThat(sentMessage().getRecipients(Message.RecipientType.CC), is(nullValue()));
    }

    @Test
    void nullCcIsAllowed() throws Exception {
        assertThat(emailService.sendEmail(FROM, "anna@example.at", null, "S", "<p/>", Map.of()), is(empty()));
        assertThat(sentMessage().getRecipients(Message.RecipientType.CC), is(nullValue()));
    }

    @Test
    void noValidRecipientIsAnErrorAndNothingIsSent() {
        MessagingException error = assertThrows(MessagingException.class,
                () -> emailService.sendEmail(FROM, "kaputt; ", "office@eeg.at", "S", "<p/>", Map.of()));

        assertThat(error.getMessage(), containsString("invalid email"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void nullRecipientIsAnError() {
        assertThrows(MessagingException.class,
                () -> emailService.sendEmail(FROM, null, null, "S", "<p/>", Map.of()));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void pngIsEmbeddedInlineAndOtherFilesAreAttachedAsPdf() throws Exception {
        Map<String, byte[]> attachments = new LinkedHashMap<>();
        attachments.put("attachment-logo.png", new byte[]{1, 2, 3});
        attachments.put("Rechnung-R202400001.pdf", new byte[]{4, 5});

        emailService.sendEmail(FROM, "anna@example.at", null, "S", "<p>Hallo <img src=\"cid:x\"/></p>",
                attachments);

        List<MimeBodyPart> parts = leafParts(sentMessage());
        MimeBodyPart logo = partWithDisposition(parts, Part.INLINE);
        assertThat(logo.getContentID(), is("<attachment-logo.png>"));
        assertThat(logo.getDataHandler().getContentType(), is("image/png"));

        MimeBodyPart pdf = partWithDisposition(parts, Part.ATTACHMENT);
        assertThat(pdf.getFileName(), is("Rechnung-R202400001.pdf"));
        assertThat(pdf.getDataHandler().getContentType(), is("application/pdf"));

        List<MimeBodyPart> bodies = parts.stream().filter(p -> header(p, "Content-ID") == null && header(p, "Content-Disposition") == null)
                .toList();
        assertThat(bodies, hasSize(1));
        assertThat((String) bodies.get(0).getContent(), containsString("Hallo"));
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static List<String> addresses(jakarta.mail.Address[] addresses) {
        return Arrays.stream(addresses).map(a -> ((InternetAddress) a).getAddress()).toList();
    }

    private static List<MimeBodyPart> leafParts(MimeMessage message) throws MessagingException, IOException {
        List<MimeBodyPart> result = new ArrayList<>();
        collect((Multipart) message.getContent(), result);
        return result;
    }

    private static void collect(Multipart multipart, List<MimeBodyPart> result)
            throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) {
                collect(nested, result);
            } else {
                result.add((MimeBodyPart) part);
            }
        }
    }

    private static MimeBodyPart partWithDisposition(List<MimeBodyPart> parts, String disposition)
            throws MessagingException {
        List<MimeBodyPart> matching = new ArrayList<>();
        for (MimeBodyPart part : parts) {
            if (disposition.equalsIgnoreCase(part.getDisposition())) {
                matching.add(part);
            }
        }
        assertThat(matching, hasSize(1));
        return matching.get(0);
    }

    private static String header(MimeBodyPart part, String name) {
        try {
            return part.getHeader(name, null);
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }
}
