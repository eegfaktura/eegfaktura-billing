package org.vfeeg.eegfaktura.billing.logging;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Text for the log from sources that may carry personal data (never-log list of the logging concept). */
public final class LogText {

    // Unicode letters and digits (international addresses); the domain may be missing ("anna@").
    // Lookbehind and possessive quantifiers: linear on a long word, this runs over every line of the log. A match takes
    // the rest of the run of address characters, or a second address glued on with + ' % would stay behind it.
    private static final Pattern EMAIL = Pattern.compile("(?<![\\w.%+'-])[\\w.%+'-]++@[\\w.%+'@-]*+",
            Pattern.UNICODE_CHARACTER_CLASS);
    /** Header and payload are enough to read a token's claims, so a cut-off token counts too. */
    private static final Pattern JWT = Pattern.compile("(?<![\\w-])eyJ[\\w-]++\\.[\\w-]++(?:\\.[\\w-]++)?");
    /**
     * {@code password=x}, {@code "password":"x"}, {@code MAIL_PASSWORD: x}; the name stays, the value goes. A value in
     * quotes ends at its closing quote, also on a later line. Without one, or after an escape, nothing tells where the value ends, so the
     * mask runs to the end of the line. No group repeats per character: Java would recurse and overflow on a long value.
     */
    private static final Pattern PASSWORD = Pattern.compile(
            "(?i)((?:passw(?:or)?d|passphrase|pwd|secret)[\"']?[ \\t]*[:=][ \\t]*)"
                    + "(?:\"[^\"\\\\]*+(?:\"|[^\\r\\n]*+)|'[^'\\\\]*+(?:'|[^\\r\\n]*+)|[^\\r\\n]*+)");
    /**
     * An Austrian IBAN has exactly 20 characters. It gets its own pattern because a metering point has the same start
     * and 33 characters ({@code AT0030000000000000000000000000001}), which fits the generic IBAN shape below.
     */
    private static final Pattern IBAN_AT = Pattern.compile(
            "(?<![A-Za-z0-9])AT\\d{2}( ?)\\d{4}(?:\\1\\d{4}){3}(?![A-Za-z0-9])");
    /**
     * Any other country: 15 to 34 characters, without blanks or with one after every fourth. The same separator
     * throughout keeps a tenant followed by numbers ({@code RC100123 4711 1}) out. Upper case only, so the lower-case
     * hex of a request id or a UUID does not match.
     */
    private static final Pattern IBAN_OTHER = Pattern.compile("(?<![A-Za-z0-9])(?=[A-Z0-9 ]{15})(?!AT)"
            + "[A-Z]{2}\\d{2}( ?)[A-Z0-9]{4}(?:\\1[A-Z0-9]{4}){1,6}(?:\\1[A-Z0-9]{1,3})?(?![A-Za-z0-9])");

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
    private static String withoutEmails(String text) {
        return text == null ? null : EMAIL.matcher(text).replaceAll("<email>");
    }

    /**
     * The filter of the log encoder: e-mail addresses, IBANs, JWTs and password values replaced, everything else
     * unchanged. Tenants, UUIDs, request ids and metering-point numbers stay readable (they are ids, W-261008-ca2).
     * An object hash like {@code Foo@1a2b} counts as an address; that loses nothing a reader needs.
     */
    public static String masked(String text) {
        if (text == null) {
            return null;
        }
        String masked = PASSWORD.matcher(text).replaceAll("$1<masked>");
        masked = JWT.matcher(masked).replaceAll("<jwt>");
        masked = withoutEmails(masked);
        masked = IBAN_AT.matcher(masked).replaceAll("<iban>");
        return IBAN_OTHER.matcher(masked).replaceAll("<iban>");
    }

    /**
     * Does the exception or one of its causes quote input values in its message (e.g. a PSQLException
     * "Failing row contains (...)")? Such an exception is logged by type only, see {@link #withoutMessages}.
     */
    public static boolean quotesInput(Throwable exception) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = exception; t != null && seen.add(t); t = t.getCause()) {
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
        return withoutMessages(exception, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /** {@code seen} ends a cause chain that runs in a circle. */
    private static Throwable withoutMessages(Throwable exception, Set<Throwable> seen) {
        if (exception == null || !seen.add(exception)) {
            return null;
        }
        MessageRemoved copy = new MessageRemoved(exception.getClass().getName(),
                withoutMessages(exception.getCause(), seen));
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
