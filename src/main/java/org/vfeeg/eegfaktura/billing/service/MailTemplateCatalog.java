package org.vfeeg.eegfaktura.billing.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The mails of billing whose text an EEG can edit (konzept-mail-texte-editierbar.md, catalog in
 * code). Today only {@code billing-document}, the mail of a billing run with the invoice or credit
 * note PDF attached.
 */
@Component
public class MailTemplateCatalog {

    public static final String BILLING_DOCUMENT = "billing-document";

    public record Placeholder(String key, String label, boolean block) {
    }

    public record Limits(int maxImages, int maxImageBytes, int maxAttachments, int maxAttachmentBytes,
                         int maxTotalBytes) {
    }

    public record Entry(String key, String name, String description, int defaultVersion,
                        String defaultSubject, String defaultBodyHtml, List<Placeholder> placeholders,
                        Limits limits) {

        public Set<String> placeholderKeys() {
            return placeholders.stream().map(Placeholder::key).collect(Collectors.toSet());
        }

        /** Placeholders whose value is HTML (sanitized when rendered, not escaped). */
        public Set<String> htmlKeys() {
            return HTML_VALUES;
        }

        public Set<String> blockKeys() {
            return placeholders.stream().filter(Placeholder::block).map(Placeholder::key)
                    .collect(Collectors.toSet());
        }
    }

    /** billing.footerText is HTML in the PDF (markup="html"), so it is HTML in the mail too. */
    private static final Set<String> HTML_VALUES = Set.of("billing.footerText");

    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_BODY_BYTES = 100 * 1024;

    /**
     * Today's text of BillingEmailDefaultTemplate.ftl in the stored format (the eegfaktura logo on
     * top is the built-in image {@code mailfile:eegfaktura-logo}). The subject keeps today's pattern
     * "Belegart Zeitraum - EEG", but the period is now the readable date range (document.period, e.g.
     * "01.07.2026 - 30.09.2026" instead of "Abr_YQ-2026-3"), and with an empty EEG name it ends in " -".
     * Raising defaultVersion marks stored texts as "default changed".
     */
    private static final String BILLING_DOCUMENT_BODY =
            "<p style=\"text-align:center\"><img src=\"mailfile:" + MailHtmlPolicy.BUILTIN_LOGO_ID
            + "\" alt=\"Logo\" /></p>"
            + "<p>Liebe/r <span data-ph=\"recipient.name\">Empfängername</span>,</p>"
            + "<p>anbei findest du deine <span data-ph=\"document.type\">Belegart</span> für den "
            + "Abrechnungszeitraum <span data-ph=\"document.period\">Abrechnungszeitraum</span>.</p>"
            + "<p>Mit lieben Grüßen,</p>"
            + "<p><span data-ph=\"eeg.name\">Name der EEG</span></p>"
            + "<p style=\"text-align:center\"><span data-ph=\"billing.footerText\">Fußzeile der Belege</span></p>";

    private final Map<String, Entry> entries;

    public MailTemplateCatalog(MailHtmlPolicy mailHtmlPolicy) {
        Entry billingDocument = new Entry(
                BILLING_DOCUMENT,
                "Rechnung / Gutschrift",
                "Wird beim Versand eines Abrechnungslaufs an jedes Mitglied mit E-Mail-Adresse verschickt. "
                        + "Der Beleg (PDF) wird immer angehängt.",
                1,
                "{document.type} {document.period} - {eeg.name}",
                // stored in the sanitizer's output form, so an unchanged default round-trips 1:1
                mailHtmlPolicy.sanitize(BILLING_DOCUMENT_BODY, Set.of()),
                List.of(
                        new Placeholder("recipient.name", "Empfängername", false),
                        new Placeholder("document.type", "Belegart", false),
                        new Placeholder("document.period", "Abrechnungszeitraum", false),
                        new Placeholder("eeg.name", "Name der EEG", false),
                        new Placeholder("eeg.email", "E-Mail der EEG", false),
                        new Placeholder("eeg.contactBlock", "Kontaktdaten der EEG", true),
                        new Placeholder("billing.footerText", "Fußzeile der Belege", false)),
                // grill-me 22.10.2026: at most 3 images, together <= 500 KB, no extra PDF
                new Limits(3, 500 * 1024, 0, 0, 500 * 1024));
        entries = Map.of(billingDocument.key(), billingDocument);
    }

    public List<Entry> all() {
        return List.copyOf(entries.values());
    }

    public Optional<Entry> find(String key) {
        return Optional.ofNullable(key == null ? null : entries.get(key));
    }
}
