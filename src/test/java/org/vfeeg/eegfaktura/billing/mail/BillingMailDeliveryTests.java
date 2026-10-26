package org.vfeeg.eegfaktura.billing.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentMailService;
import org.vfeeg.eegfaktura.billing.service.MailTemplateCatalog;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService;
import org.vfeeg.eegfaktura.billing.support.ConcurrencyAndMailBase;
import org.vfeeg.eegfaktura.billing.support.GreenMailHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * {@link BillingDocumentMailService#sendAllBillingDocuments} against a real SMTP server (GreenMail): the
 * documents of a real final run with their PDFs, the real default text and fixed footer. Known-errors #18 (F7) for the
 * status after failures.
 */
class BillingMailDeliveryTests extends ConcurrencyAndMailBase {

    private static final String MEMBER_1 = "member1@example.org";
    private static final String MEMBER_2 = "member2@example.org";
    private static final String COMMUNITY = "office@eeg.example";

    @Test
    void documentGoesOutWithPdfAndFixedFooter() throws Exception {
        UUID runId = finalRun(members(MEMBER_1));

        String protocol = mailService.sendAllBillingDocuments(runId);

        assertThat(protocol, containsString(MEMBER_1 + " OK,"));
        MimeMessage[] received = receivedBy(MEMBER_1);
        assertThat(received, arrayWithSize(1));
        MimeMessage mail = received[0];
        assertThat(mail.getFrom(), arrayContaining(new InternetAddress("no-reply@eegfaktura.at")));
        assertThat(mail.getRecipients(MimeMessage.RecipientType.TO), arrayContaining(new InternetAddress(MEMBER_1)));
        assertThat(mail.getRecipients(MimeMessage.RecipientType.CC), arrayContaining(new InternetAddress(COMMUNITY)));
        assertThat(receivedBy(COMMUNITY), arrayWithSize(1));
        // the issuer since 1.0.8; the period as readable date range since the mail texts
        assertThat(mail.getSubject(), is("Rechnung 01.04.2024 - 30.06.2024 - Energiegemeinschaft Holy Grail"));

        List<Part> parts = leaves(mail);
        Part pdf = parts.stream().filter(p -> isType(p, "application/pdf")).findFirst()
                .orElseThrow(() -> new AssertionError("no PDF part"));
        assertThat(pdf.getDisposition(), is(Part.ATTACHMENT));
        assertThat(pdf.getFileName(), is(PERIOD + "_Rechnung_TRECH202400042.pdf"));
        assertThat(new String(pdf.getInputStream().readNBytes(5)), is("%PDF-"));
        // the default text has no eegfaktura logo since defaultVersion 2; the fixed footer names the sender
        assertThat(parts.stream().noneMatch(p -> isType(p, "image/png")), is(true));
        String html = parts.stream().filter(p -> isType(p, "text/html")).findFirst()
                .map(BillingMailDeliveryTests::text).orElseThrow(() -> new AssertionError("no HTML body"));
        assertThat(html, not(containsString("cid:" + MailTemplateService.LOGO_CONTENT_ID)));
        assertThat(html, containsString("Nachname1"));
        assertThat(html, containsString(MailTemplateCatalog.FIXED_FOOTER_HTML));
        String plain = parts.stream().filter(p -> isType(p, "text/plain")).findFirst()
                .map(BillingMailDeliveryTests::text).orElseThrow(() -> new AssertionError("no text body"));
        assertThat(plain, containsString(MailTemplateCatalog.FIXED_FOOTER));

        BillingRunDTO run = run(runId);
        assertThat(run.getMailStatus(), is("SENT"));
        assertThat(run.getMailStatusDateTime(), is(notNullValue()));
        assertThat(run.getSendMailProtocol(), is(protocol));
    }

    /** Valid parts are delivered, invalid ones reported; a member without any valid address is an error line. */
    @Test
    void invalidAddressesAreReportedAndValidOnesDelivered() throws Exception {
        UUID runId = finalRun(members(MEMBER_1 + "; kein-gueltiger-eintrag", "kaputt"));

        String protocol = mailService.sendAllBillingDocuments(runId);

        assertThat(protocol, containsString(MEMBER_1 + "; kein-gueltiger-eintrag OK"
                + " (nicht zugestellt an ungültige Adresse: kein-gueltiger-eintrag),"));
        assertThat(protocol, containsString("kaputt (MitgliedsNr , Vorname2 Nachname2) FEHLER,"));
        assertThat(receivedBy(MEMBER_1), arrayWithSize(1));
        assertThat(receivedBy(MEMBER_1)[0].getRecipients(MimeMessage.RecipientType.TO),
                arrayContaining(new InternetAddress(MEMBER_1)));
        assertThat("one mail to member 1, its cc to the community", allReceived(), is(2));
        assertThat(run(runId).getMailStatus(), is("SENT"));
    }

    /**
     * The SMTP server goes away after the first mail: the first member got the mail, the second is an error
     * line, and the run does not stay "IN PROGRESS" (part of #18 that holds today).
     */
    @Test
    void smtpFailureMidBatchIsReportedAndTheStatusIsFinal() throws Exception {
        UUID runId = finalRun(members(MEMBER_1, MEMBER_2));
        int down = closedPort();
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object rejected = invocation.callRealMethod();
            if (first.getAndSet(false)) {
                mailSender.setPort(down);
            }
            return rejected;
        }).when(emailServiceSpy).sendEmail(anyString(), anyString(), any(), anyString(), anyString(), anyList(), anyMap());

        String protocol = mailService.sendAllBillingDocuments(runId);

        assertThat(countOf(protocol, " OK,"), is(1));
        assertThat(countOf(protocol, " FEHLER,"), is(1));
        assertThat(receivedBy(MEMBER_1).length + receivedBy(MEMBER_2).length, is(1));
        BillingRunDTO run = run(runId);
        assertThat(run.getMailStatus(), is(not("IN PROGRESS")));
        assertThat(run.getSendMailProtocol(), is(protocol));
    }

    /**
     * F7 (#18): the SMTP server is down for the whole batch, every mail fails. Correct: the run is not
     * marked "SENT" (nothing was sent) and not left "IN PROGRESS"; today it is "SENT", which also blocks
     * a second attempt.
     */
    @Test
    @Disabled("known-errors #18")
    void runWhereEverySmtpDeliveryFailedIsNotMarkedSent() {
        UUID runId = finalRun(members(MEMBER_1, MEMBER_2));
        mailSender.setPort(closedPort());

        String protocol = mailService.sendAllBillingDocuments(runId);

        assertThat(countOf(protocol, " FEHLER,"), is(2));
        assertThat(allReceived(), is(0));
        assertThat(run(runId).getMailStatus(), is(not(anyOf(is("SENT"), is("IN PROGRESS")))));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static int countOf(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    private static boolean isType(Part part, String type) {
        try {
            return part.isMimeType(type);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** All non-multipart parts of a message, depth first. */
    private static List<Part> leaves(Part part) throws Exception {
        List<Part> result = new ArrayList<>();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                result.addAll(leaves(child));
            }
        } else {
            result.add(part);
        }
        return result;
    }

    private static String text(Part part) {
        try {
            return (String) part.getContent();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int allReceived() {
        return GreenMailHolder.GREEN_MAIL.getReceivedMessages().length;
    }
}
