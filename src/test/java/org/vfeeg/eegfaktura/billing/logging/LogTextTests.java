package org.vfeeg.eegfaktura.billing.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogTextTests {

    @Test
    void everyAddressIsMasked() {
        assertEquals("Invalid Addresses: <email>; <<email>>: rejected",
                LogText.withoutEmails("Invalid Addresses: anna.muster+eeg@example.at; <bert@mail.example.org>: rejected"));
    }

    @Test
    void internationalAddressesAndLocalPartsAreMasked() {
        assertEquals("<email> and <email> and <email>",
                LogText.withoutEmails("jürgen.müller@bäckerei.at and o'neil@example.org and anna@"));
    }

    @Test
    void textWithoutAddressStays() {
        assertEquals("smtp down (connect timed out)", LogText.withoutEmails("smtp down (connect timed out)"));
        assertNull(LogText.withoutEmails(null));
    }
}
