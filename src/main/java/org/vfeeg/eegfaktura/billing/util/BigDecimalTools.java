package org.vfeeg.eegfaktura.billing.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BigDecimalTools {

    // "21.345,00" (platform#250); a new instance per call instead of a shared, changeable public field
    private static DecimalFormat germanFormat() {
        return new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.GERMAN));
    }

    public static BigDecimal makeZeroIfNull(BigDecimal bigDecimal) {
        return bigDecimal == null ? BigDecimal.ZERO : bigDecimal;
    }

    public static boolean isNullOrZero(BigDecimal bigDecimal) {
        return bigDecimal==null || bigDecimal.signum()==0;
    }
    public static String makeGermanString(BigDecimal bigDecimal) {
        if (bigDecimal==null) return "";
        return germanFormat().format(bigDecimal);
    }

    public static String makeGermanString(BigDecimal bigDecimal, String unit) {
        if (bigDecimal==null) return "";
        return germanFormat().format(bigDecimal)+(unit == null || unit.isEmpty() ? "" : " "+unit);
    }

}
