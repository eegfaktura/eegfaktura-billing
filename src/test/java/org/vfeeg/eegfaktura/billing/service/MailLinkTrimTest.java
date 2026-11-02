package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Word and URL trimming of the link rules: same result as the backend, linear on long runs. */
class MailLinkTrimTest {

    @Test
    void stripWordRemovesPunctuationAndSymbolsAtBothEndsButKeepsAFinalSlash() {
        assertEquals("bank.at", MailLinkRules.stripWord("»bank.at«"));
        assertEquals("bank.at", MailLinkRules.stripWord("→bank.at…*"));
        assertEquals("eeg.at/", MailLinkRules.stripWord("(eeg.at/"));
        assertEquals("a!!b", MailLinkRules.stripWord("a!!b"));
        assertEquals("", MailLinkRules.stripWord("!!!"));
    }

    @Test
    void trimSpacesRemovesUnicodeAndAsciiSpacesAtBothEnds() {
        assertEquals("https://eeg.at", MailHtmlPolicy.trimSpaces(" \t https://eeg.at \r\n"));
        assertEquals("a   b", MailHtmlPolicy.trimSpaces(" a   b "));
    }

    @Test
    void longRunsInsideAWordOrUrlStayFast() {
        String word = "a" + "!".repeat(90_000) + "b";
        String url = "https://eeg.at/a" + " ".repeat(90_000) + "b";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals(word, MailLinkRules.stripWord(word));
            assertEquals(url, MailHtmlPolicy.trimSpaces(url));
        });
    }
}
