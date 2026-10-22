package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared case list of the mail HTML rules (rules.md / konzept-mail-texte-editierbar.md, section H);
 * eegfaktura-backend tests the same cases against the same mail-html-policy.json.
 */
class MailHtmlPolicyTest {

    private static final String OWN_FILE = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String FOREIGN_FILE = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    private final MailHtmlPolicy policy = new MailHtmlPolicy();

    private String clean(String html) {
        return policy.sanitize(html, Set.of(OWN_FILE));
    }

    @Test
    @DisplayName("script, style, iframe are removed including their content")
    void dangerousElementsRemoved() {
        assertEquals("<p>a</p>", clean("<p>a<script>alert(1)</script></p>"));
        assertEquals("<p>a</p>", clean("<p>a</p><style>p{color:red}</style>"));
        assertEquals("<p>a</p>", clean("<p>a</p><iframe src=\"https://x\"></iframe>"));
    }

    @Test
    @DisplayName("on* attributes are removed")
    void eventHandlersRemoved() {
        assertEquals("<p>a</p>", clean("<p onclick=\"alert(1)\">a</p>"));
        assertEquals("<strong>a</strong>", clean("<strong onmouseover=\"x()\">a</strong>"));
    }

    @Test
    @DisplayName("javascript:, data: and http: links are removed, the text stays")
    void badLinksRemoved() {
        assertEquals("<p>x</p>", clean("<p><a href=\"javascript:alert(1)\">x</a></p>"));
        assertEquals("<p>x</p>", clean("<p><a href=\"JaVaScRiPt:alert(1)\">x</a></p>"));
        assertEquals("<p>x</p>", clean("<p><a href=\"data:text/html;base64,PHNjcmlwdD4=\">x</a></p>"));
        assertEquals("<p>x</p>", clean("<p><a href=\"http://example.com\">x</a></p>"));
        assertEquals("<p>x</p>", clean("<p><a href=\"/relative\">x</a></p>"));
    }

    @Test
    @DisplayName("https: and mailto: links are kept, with rel")
    void goodLinksKept() {
        String https = clean("<p><a href=\"https://example.com/a?b=1\">x</a></p>");
        assertTrue(https.contains("href=\"https://example.com/a?b&#61;1\"") || https.contains("href=\"https://example.com/a?b=1\""), https);
        assertTrue(https.contains("rel=\"noopener noreferrer\""), https);
        String mailto = clean("<p><a href=\"mailto:office@example.com\">x</a></p>");
        assertTrue(mailto.contains("href=\"mailto:office&#64;example.com\"") || mailto.contains("href=\"mailto:office@example.com\""), mailto);
        assertTrue(mailto.contains("rel=\"noopener noreferrer\""), mailto);
    }

    @Test
    @DisplayName("external images are removed, mailfile images only for files of this tenant and mail")
    void images() {
        assertEquals("<p>a</p>", clean("<p>a<img src=\"https://tracker.example/x.png\" /></p>"));
        assertEquals("<p>a</p>", clean("<p>a<img src=\"http://tracker.example/x.png\" /></p>"));
        assertEquals("<p>a</p>", clean("<p>a<img src=\"data:image/png;base64,AAAA\" /></p>"));
        assertEquals("<p>a</p>", clean("<p>a<img src=\"mailfile:" + FOREIGN_FILE + "\" /></p>"));
        assertEquals("<p>a</p>", clean("<p>a<img src=\"mailfile:not-a-uuid\" /></p>"));
        assertEquals("<p>a<img src=\"mailfile:" + OWN_FILE + "\" alt=\"Logo\" width=\"200\" /></p>",
                clean("<p>a<img src=\"mailfile:" + OWN_FILE.toUpperCase() + "\" alt=\"Logo\" width=\"200\" onerror=\"x()\" /></p>"));
    }

    @Test
    @DisplayName("style: only text-align left/center/right on p, h1-h3, li")
    void styles() {
        assertEquals("<p style=\"text-align:center\">a</p>", clean("<p style=\"text-align: center;\">a</p>"));
        assertEquals("<h2 style=\"text-align:right\">a</h2>", clean("<h2 style=\"TEXT-ALIGN:RIGHT\">a</h2>"));
        assertEquals("<li style=\"text-align:left\">a</li>", clean("<li style=\"color:red;text-align:left\">a</li>").replace("<ul>", "").replace("</ul>", ""));
        assertEquals("<p>a</p>", clean("<p style=\"color:red\">a</p>"));
        assertEquals("<p>a</p>", clean("<p style=\"text-align:justify\">a</p>"));
        assertEquals("<p>a</p>", clean("<p style=\"background:url(javascript:alert(1))\">a</p>"));
        assertEquals("<strong>a</strong>", clean("<strong style=\"text-align:center\">a</strong>"));
    }

    @Test
    @DisplayName("span/div with data-ph are kept, without data-ph they are unwrapped")
    void placeholders() {
        assertEquals("<p>Hallo <span data-ph=\"recipient.name\">Name</span></p>",
                clean("<p>Hallo <span data-ph=\"recipient.name\" class=\"x\">Name</span></p>"));
        assertEquals("<div data-ph=\"eeg.contactBlock\">Kontakt</div>",
                clean("<div data-ph=\"eeg.contactBlock\">Kontakt</div>"));
        assertEquals("<p>Hallo Name</p>", clean("<p>Hallo <span class=\"x\">Name</span></p>"));
        assertEquals("<p>Text</p>", clean("<div><p>Text</p></div>"));
        // an invalid key is dropped, the span then has no attribute left and is unwrapped
        assertEquals("<p>Name</p>", clean("<p><span data-ph=\"x&quot;><script>\">Name</span></p>"));
    }

    @Test
    @DisplayName("inspect finds placeholders and images; unknown keys are kept for the 400 check")
    void inspect() {
        String html = clean("<p><span data-ph=\"member.unknown\">x</span><img src=\"mailfile:" + OWN_FILE + "\" /></p>");
        MailHtmlPolicy.Inspection inspection = policy.inspect(html);
        assertEquals(Set.of("member.unknown"), inspection.placeholderKeys());
        assertEquals(Set.of(OWN_FILE), inspection.fileIds());
        assertFalse(inspection.isEmpty());
        assertTrue(policy.inspect(clean("<p> <script>x</script></p><br>")).isEmpty());
    }
}
