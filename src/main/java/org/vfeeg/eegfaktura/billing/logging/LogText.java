package org.vfeeg.eegfaktura.billing.logging;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/** Text for the log from sources that may carry personal data (never-log list of the logging concept). */
public final class LogText {

    // Unicode letters and digits (international addresses); the domain may be missing ("anna@")
    private static final Pattern EMAIL = Pattern.compile("[\\w.%+'-]+@[\\w.-]*", Pattern.UNICODE_CHARACTER_CLASS);

    /** Exceptions whose message quotes input values: request body, parameter or database row. */
    private static final List<Class<? extends Throwable>> QUOTING_INPUT = List.of(
            HttpMessageNotReadableException.class, DataIntegrityViolationException.class, TypeMismatchException.class,
            jakarta.validation.ConstraintViolationException.class,
            org.hibernate.exception.ConstraintViolationException.class, SQLException.class);

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

    /**
     * Does the exception or one of its causes quote input values in its message (e.g. a PSQLException
     * "Failing row contains (...)")? Such an exception is logged by type only, see {@link #withoutMessages}.
     */
    public static boolean quotesInput(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause() == t ? null : t.getCause()) {
            for (Class<? extends Throwable> type : QUOTING_INPUT) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A copy of the exception chain for the log: same stack frames, each message replaced by the original type.
     * For exceptions that {@link #quotesInput quote input values}.
     */
    public static Throwable withoutMessages(Throwable exception) {
        if (exception == null) {
            return null;
        }
        Throwable cause = exception.getCause() == exception ? null : exception.getCause();
        MessageRemoved copy = new MessageRemoved(exception.getClass().getName(), withoutMessages(cause));
        copy.setStackTrace(exception.getStackTrace());
        return copy;
    }

    /** Stands for an exception whose message was removed; its message is the original type. */
    public static final class MessageRemoved extends RuntimeException {
        MessageRemoved(String originalType, Throwable cause) {
            super(originalType + " (message not logged, it may quote input values)", cause);
        }
    }
}
