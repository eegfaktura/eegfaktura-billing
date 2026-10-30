package org.vfeeg.eegfaktura.billing.logging;

import java.util.regex.Pattern;

/** Text for the log from sources that may carry personal data (never-log list of the logging concept). */
public final class LogText {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

    private LogText() {
    }

    /** The text with every e-mail address replaced by {@code <email>} — e.g. an SMTP error naming the recipient. */
    public static String withoutEmails(String text) {
        return text == null ? null : EMAIL.matcher(text).replaceAll("<email>");
    }
}
