package org.vfeeg.eegfaktura.billing.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The months a clearing period identifier covers (form: see {@link ClearingPeriodIdentifierTool}). The type in
 * the identifier decides: a run of March and one of the first quarter have different identifiers but the same
 * energy of March (eegfaktura-dev tracking billing 261031-mm5).
 */
public final class ClearingPeriodMonths {

    private static final Pattern IDENTIFIER = Pattern.compile("Abr_(Y|YH|YQ|YM)-(\\d{4})(?:-(\\d{1,2}))?");

    private final int first;
    private final int last;

    private ClearingPeriodMonths(int first, int last) {
        this.first = first;
        this.last = last;
    }

    /** Empty for an identifier of another form: such a period cannot be compared. */
    public static Optional<ClearingPeriodMonths> of(String identifier) {
        Matcher matcher = identifier == null ? null : IDENTIFIER.matcher(identifier);
        if (matcher == null || !matcher.matches()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(matcher.group(2));
        int period = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return switch (matcher.group(1)) {
            case "Y" -> months(year, 1, 12);
            case "YH" -> period >= 1 && period <= 2 ? months(year, period * 6 - 5, period * 6) : Optional.empty();
            case "YQ" -> period >= 1 && period <= 4 ? months(year, period * 3 - 2, period * 3) : Optional.empty();
            default -> period >= 1 && period <= 12 ? months(year, period, period) : Optional.empty();
        };
    }

    private static Optional<ClearingPeriodMonths> months(int year, int firstMonth, int lastMonth) {
        return Optional.of(new ClearingPeriodMonths(year * 12 + firstMonth, year * 12 + lastMonth));
    }

    public boolean overlaps(ClearingPeriodMonths other) {
        return first <= other.last && other.first <= last;
    }
}
