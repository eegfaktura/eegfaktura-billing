package org.vfeeg.eegfaktura.billing.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailAddressUtilTest {

    @Test
    void normalizeStripsOuterWhitespacePerPart() {
        assertEquals(List.of("a@x.at"), EmailAddressUtil.normalize(" a@x.at "));
        assertEquals(List.of("a@x.at", "b@y.at"), EmailAddressUtil.normalize("a@x.at ; b@y.at"));
        // NBSP (U+00A0) — String#trim would keep it, strip() removes it
        assertEquals(List.of("a@x.at"), EmailAddressUtil.normalize(" a@x.at "));
        assertEquals(List.of(), EmailAddressUtil.normalize("  "));
        assertEquals(List.of(), EmailAddressUtil.normalize(null));
    }

    @Test
    void isValidAppliesSharedRule() {
        assertTrue(EmailAddressUtil.isValid("a@x.at"));
        assertTrue(EmailAddressUtil.isValid("A@X.AT"));
        assertTrue(EmailAddressUtil.isValid("a@eeg.energy")); // modern gTLD, no allowlist
        assertFalse(EmailAddressUtil.isValid("x"));
        assertFalse(EmailAddressUtil.isValid("hedwig.schön@x.at")); // non-ASCII local part
        assertFalse(EmailAddressUtil.isValid("a b@x.at")); // inner whitespace
        assertFalse(EmailAddressUtil.isValid("a@x")); // missing TLD
    }
}
