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
                        Map.of("recipient.name", "Max\r\n", "eeg.name", "EEG"), keys));
        assertEquals("<b>", renderer.renderSubject("{recipient.name}", Map.of("recipient.name", "<b>"), keys));
        assertEquals("a", renderer.renderSubject("a {eeg.name}", Map.of(), keys));
        assertEquals(Set.of("recipient.name", "x"), MailTemplateRenderer.subjectPlaceholders("{recipient.name} {x} { y }"));
    }

    @Test
    void unknownPlaceholderThrows() {
        assertThrows(IllegalStateException.class, () -> renderer.renderSubject("{member.iban}", Map.of(), keys));
        assertThrows(IllegalStateException.class, () -> renderer.renderBody(
                "<p><span data-ph=\"member.iban\">x</span></p>", Set.of(), Map.of(), keys, Set.of(), id -> null));
    }

    @Test
    void bodyValuesAreEscapedAndReplaceTheWholeElement() {
        String html = renderer.renderBody(
                "<p>Hallo <span data-ph=\"recipient.name\">Label <strong>fett</strong><br />x</span>!</p>"
                        + "<div data-ph=\"eeg.contactBlock\">Kontakt</div>",
                Set.of(), Map.of("recipient.name", "<script>alert(1)</script>", "eeg.contactBlock", "Weg 1\n1234 Ort"),
                keys, Set.of("eeg.contactBlock"), id -> null);
        assertEquals("<p>Hallo &lt;script&gt;alert(1)&lt;/script&gt;!</p><div>Weg 1<br />1234 Ort</div>", html);
        assertFalse(html.contains("Label"));
    }

    @Test
    void missingValueIsEmptyNeverTheRawKey() {
        String html = renderer.renderBody("<p>A<span data-ph=\"eeg.name\">Name</span>B</p>",
                Set.of(), Map.of(), keys, Set.of(), id -> null);
        assertEquals("<p>AB</p>", html);
        assertTrue(renderer.frame(html, null).contains("<p>AB</p>"));
    }
}
