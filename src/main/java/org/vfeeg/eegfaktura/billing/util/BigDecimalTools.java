package org.vfeeg.eegfaktura.billing.util;

import io.netty.util.internal.StringUtil;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BigDecimalTools {

    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat(
            "#0.00", new DecimalFormatSymbols(Locale.GERMAN));

    public static BigDecimal makeZeroIfNull(BigDecimal bigDecimal) {
        return bigDecimal == null ? BigDecimal.ZERO : bigDecimal;
    }

    public static boolean isNullOrZero(BigDecimal bigDecimal) {
        return bigDecimal==null || bigDecimal.signum()==0;
    }
    public static String makeGermanString(BigDecimal bigDecimal) {
        if (bigDecimal==null) return "";
        return DECIMAL_FORMAT.format(bigDecimal);
    }

    public static String makeGermanString(BigDecimal bigDecimal, String unit) {
        if (bigDecimal==null) return "";
        return DECIMAL_FORMAT.format(bigDecimal)+(StringUtil.isNullOrEmpty(unit) ? "" : " "+unit);
    }

}
