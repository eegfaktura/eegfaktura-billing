package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailServiceTest {

    @Test
    void plainTextDecodesNamedAndNumericEntities() {
        assertEquals("office@eeg.at +43 1 234\nA & B <x> \"q\" 'a' Grüße ß",
                EmailService.htmlToPlainText("<p>office&#64;eeg.at &#43;43&nbsp;1 234</p>"
                        + "<p>A &amp; B &lt;x&gt; &quot;q&quot; &#39;a&#39; Gr&uuml;&szlig;e &#xdf;</p>"));
    }
}
