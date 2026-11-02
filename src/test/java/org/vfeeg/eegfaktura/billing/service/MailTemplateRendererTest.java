package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailTemplateRendererTest {

    private final MailTemplateRenderer renderer = new MailTemplateRenderer(new MailHtmlPolicy());
    private final Set<String> keys = Set.of("recipient.name", "eeg.name", "eeg.contactBlock");

    @Test
    void subjectTokensArePlainTextOnOneLine() {
        assertEquals("Hallo Max - EEG } { {1}",
                renderer.renderSubject("Hallo {recipient.name} - {eeg.name} } { {1}",
                        Map.of("recipient.name", "Max\r\n", "eeg.name", "EEG"), keys, Set.of()));
        assertEquals("<b>", renderer.renderSubject("{recipient.name}", Map.of("recipient.name", "<b>"), keys, Set.of()));
        assertEquals("a", renderer.renderSubject("a {eeg.name}", Map.of(), keys, Set.of()));
        assertEquals(Set.of("recipient.name", "x"),
                MailTemplateRenderer.subjectPlaceholders("{recipient.name} {x} { y } {EEG} {a1} {a.} {.a}"));
        assertEquals("EEG {EEG} {1}", renderer.renderSubject("{eeg.name} {EEG} {1}", Map.of("eeg.name", "EEG"), keys, Set.of()));
    }

    @Test
    void unknownPlaceholderThrows() {
        assertThrows(IllegalStateException.class, () -> renderer.renderSubject("{member.iban}", Map.of(), keys, Set.of()));
        assertThrows(IllegalStateException.class, () -> renderer.renderBody(
                "<p><span data-ph=\"member.iban\">x</span></p>", Map.of(), keys, Set.of(), Set.of(), id -> null));
    }

    @Test
    void bodyValuesAreEscapedAndReplaceTheWholeElement() {
        String html = renderer.renderBody(
                "<p>Hallo <span data-ph=\"recipient.name\">Label <strong>fett</strong><br />x</span>!</p>"
                        + "<div data-ph=\"eeg.contactBlock\">Kontakt</div>",
                Map.of("recipient.name", "<script>alert(1)</script>", "eeg.contactBlock", "Weg 1\n1234 Ort"),
                keys, Set.of("eeg.contactBlock"), Set.of(), id -> null);
        assertEquals("<p>Hallo &lt;script&gt;alert(1)&lt;/script&gt;!</p><div>Weg 1<br />1234 Ort</div>", html);
        assertFalse(html.contains("Label"));
    }

    @Test
    void htmlValueIsSanitizedNotEscaped() {
        Set<String> footer = Set.of("billing.footerText");
        Set<String> known = Set.of("billing.footerText");
        String html = renderer.renderBody("<p><span data-ph=\"billing.footerText\">F</span></p>",
                Map.of("billing.footerText", "<b>Sitz</b> A &amp; B\nZVR <script>x()</script>1<img src=\"https://t/x.png\">"),
                known, Set.of(), footer, id -> null);
        assertEquals("<p><b>Sitz</b> A &amp; B<br />ZVR 1</p>", html);
        assertEquals("Sitz A & B ZVR 1", renderer.renderSubject("{billing.footerText}",
                Map.of("billing.footerText", "<b>Sitz</b> A &amp; B\nZVR <script>x()</script>1"), known, footer));
    }

    @Test
    void missingValueIsEmptyNeverTheRawKey() {
        String html = renderer.renderBody("<p>A<span data-ph=\"eeg.name\">Name</span>B</p>",
                Map.of(), keys, Set.of(), Set.of(), id -> null);
        assertEquals("<p>AB</p>", html);
        assertTrue(renderer.frame(html).contains("<p>AB</p>"));
    }
}
