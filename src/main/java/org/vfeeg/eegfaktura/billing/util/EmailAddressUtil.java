package org.vfeeg.eegfaktura.billing.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared e-mail address rule of the eegfaktura suite (backend, eda-xp,
 * billing, web): per ';'-separated part — outer unicode whitespace
 * (incl. NBSP) stripped, ASCII local part, TLD of at least two letters,
 * no TLD allowlist.
 */
public final class EmailAddressUtil {

    private static final Pattern ADDRESS_PART = Pattern.compile(
            "^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$", Pattern.CASE_INSENSITIVE);

    private EmailAddressUtil() {
    }

    /**
     * Splits a ';'-separated address list, strips outer whitespace per
     * part and drops empty parts. The non-breaking spaces U+00A0/
     * U+202F/U+2007 (typical Excel & copy-paste artifacts) are mapped
     * to a space first — neither String#trim nor String#strip treats
     * them as whitespace ({@code Character.isWhitespace} excludes
     * non-breaking spaces).
     */
    public static List<String> normalize(String raw) {
        if (raw == null) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(";"))
                .map(s -> s.replaceAll("[\u00A0\u202F\u2007]", " ").strip())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static boolean isValid(String part) {
        return ADDRESS_PART.matcher(part).matches();
    }
}
