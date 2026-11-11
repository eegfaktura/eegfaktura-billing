package org.vfeeg.eegfaktura.billing.logging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LogTextTests {

    @Test
    void everyAddressIsMasked() {
        assertEquals("Invalid Addresses: <email>; <<email>>: rejected",
                LogText.masked("Invalid Addresses: anna.muster+eeg@example.at; <bert@mail.example.org>: rejected"));
    }

    @Test
    void internationalAddressesAndLocalPartsAreMasked() {
        assertEquals("<email> and <email> and <email>",
                LogText.masked("jürgen.müller@bäckerei.at and o'neil@example.org and anna@"));
    }

    @Test
    void addressesJoinedByACharacterOfALocalPartAreAllMasked() {
        for (String joined : new String[]{"a@b.at+c@d.at", "a@b.at'c@d.at", "a@b.at%c@d.at", "a@b.at-c@d.at", "a@b@c.at"}) {
            assertEquals("<email>", LogText.masked(joined), joined);
        }
    }

    @Test
    void textWithoutAddressStays() {
        assertEquals("smtp down (connect timed out)", LogText.masked("smtp down (connect timed out)"));
        assertNull(LogText.masked(null));
    }

    @Test
    void maskedReplacesEveryPattern() {
        assertEquals("to <email> failed", LogText.masked("to anna.muster@example.at failed"));
        assertEquals("IBAN <iban>, again <iban>",
                LogText.masked("IBAN AT611904300234573201, again AT61 1904 3002 3457 3201"));
        assertEquals("<iban> <iban> <iban>",
                LogText.masked("DE89370400440532013000 IT60 X054 2811 1010 0000 0123 456 NO9386011117947"));
        assertEquals("iban=<iban>", LogText.masked("iban=AT483200000012345864"));
        assertEquals("token <jwt> rejected", LogText.masked("token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln-_x rejected"));
        assertEquals("cut off <jwt>", LogText.masked("cut off eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0"));
    }

    @Test
    void maskedKeepsTheNameOfAPasswordAndDropsTheValue() {
        assertEquals("password=<masked>", LogText.masked("password=s3cret! port=25"));
        assertEquals("MAIL_PASSWORD: <masked>", LogText.masked("MAIL_PASSWORD: s3cret"));
        assertEquals("{\"password\":<masked>,\"a\":1}", LogText.masked("{\"password\":\"two words\",\"a\":1}"));
        assertEquals("pwd = <masked>", LogText.masked("pwd = 'two words'"));
        assertEquals("client_secret=<masked>", LogText.masked("client_secret=abc"));
        assertEquals("jdbc:postgresql://db/billing?user=a&password=<masked>",
                LogText.masked("jdbc:postgresql://db/billing?user=a&password=s3cret&ssl=true"));
    }

    @Test
    void maskedRunsToTheEndOfThePasswordValue() {
        assertEquals("password=<masked>", LogText.masked("password=two words"));
        assertEquals("password=<masked>\nnext line", LogText.masked("password=two words\nnext line"));
        assertEquals("password=<masked>\r\nnext line", LogText.masked("password=two words\r\nnext line"));
        assertEquals("password=<masked>", LogText.masked("password=\"two words"));
        assertEquals("password=<masked>", LogText.masked("password='two words"));
        assertEquals("password=<masked>", LogText.masked("password=\"two \\\" more words\", \"a\":1"));
        assertEquals("password=<masked>", LogText.masked("password='two \\' more words'"));
        assertEquals("password=<masked>, next", LogText.masked("password=\"two words\", next"));
    }

    @Test
    void maskedStaysLinearOnALongPasswordValue() {
        String escapes = "password=\"" + "\\\"".repeat(1_000_000);
        assertEquals("password=<masked>", assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LogText.masked(escapes)));
    }

    @Test
    void maskedKeepsIds() {
        for (String id : new String[]{"RC100123", "GC100123-001", "TE100200", "AT003000",
                "AT0030000000000000000000000000001", "AT0099990000000000000000000000002",
                "RC100123 4711 1", "RC100123 4711 1234 5678",
                "1f0c2a64-7a0e-4c55-9d7a-2b1c3d4e5f60", "1F0C2A64-7A0E-4C55-9D7A-2B1C3D4E5F60",
                "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", "ATU12345678", "ERZ1234000000000089200011",
                "GET /api/fileData/{id} 200", "password expired", "Mail template of RC100123 not usable",
                "at org.vfeeg.eegfaktura.billing.Foo.setPassword(Foo.java:42)"}) {
            assertEquals(id, LogText.masked(id));
        }
    }

    @Test
    void maskedStaysLinearOnALongWord() {
        String word = "a".repeat(2_000_000);
        assertEquals(word, assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LogText.masked(word)));
    }

    @Test
    void maskedOfNullIsNull() {
        assertNull(LogText.masked(null));
    }

    @Test
    void aCircularCauseChainEnds() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertEquals(false, LogText.quotesInput(first)));
        assertEquals(2, depth(LogText.withoutMessages(first)));
    }

    private static int depth(Throwable exception) {
        return exception == null ? 0 : 1 + depth(exception.getCause());
    }

    @Test
    void aQuotedValueEndsAtItsQuoteOnALaterLine() {
        assertEquals("password=<masked>\nnext", LogText.masked("password=\"first\nsecond\"\nnext"));
    }

    @Test
    void anEmptyValueLeavesTheNextLine() {
        assertEquals("Missing password:<masked>\n\tat Foo.bar", LogText.masked("Missing password:\n\tat Foo.bar"));
    }
}
