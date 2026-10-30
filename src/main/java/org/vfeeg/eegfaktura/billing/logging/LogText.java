package org.vfeeg.eegfaktura.billing.logging;

import java.util.regex.Pattern;

/** Text for the log from sources that may carry personal data (never-log list of the logging concept). */
public final class LogText {

    // Unicode letters and digits (international addresses); the domain may be missing ("anna@")
    private static final Pattern EMAIL = Pattern.compile("[\\w.%+'-]+@[\\w.-]*", Pattern.UNICODE_CHARACTER_CLASS);

    private LogText() {
    }

    /**
     * The text with every e-mail address (also a local part with {@code @} but no domain) replaced by
     * {@code <email>} — e.g. an SMTP error naming the recipient. A second line of defence: callers should not put
     * addresses into messages in the first place.
     */
    public static String withoutEmails(String text) {
        return text == null ? null : EMAIL.matcher(text).replaceAll("<email>");
    }
}
