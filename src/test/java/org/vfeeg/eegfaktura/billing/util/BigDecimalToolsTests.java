package org.vfeeg.eegfaktura.billing.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * The German number strings are printed on every invoice and credit note (BillingPdfService).
 * Rounding: DecimalFormat's default HALF_EVEN on the exact BigDecimal value; no thousands separator.
 * Whether the print should round HALF_UP is part of the rounding question open-points B-13.
 */
class BigDecimalToolsTests {

    @Test
    void makeZeroIfNullReplacesOnlyNull() {
        assertThat(BigDecimalTools.makeZeroIfNull(null), is(sameInstance(BigDecimal.ZERO)));
        BigDecimal value = new BigDecimal("12.34");
        assertThat(BigDecimalTools.makeZeroIfNull(value), is(sameInstance(value)));
        assertThat(BigDecimalTools.makeZeroIfNull(new BigDecimal("0.00")), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    void isNullOrZeroIgnoresScale() {
        assertThat(BigDecimalTools.isNullOrZero(null), is(true));
        assertThat(BigDecimalTools.isNullOrZero(BigDecimal.ZERO), is(true));
        assertThat(BigDecimalTools.isNullOrZero(new BigDecimal("0.000")), is(true));
        assertThat(BigDecimalTools.isNullOrZero(new BigDecimal("0.01")), is(false));
        assertThat(BigDecimalTools.isNullOrZero(new BigDecimal("-0.01")), is(false));
    }

    @Test
    void germanStringUsesCommaAndTwoDecimals() {
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("1.5")), is("1,50"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("12")), is("12,00"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("0.1")), is("0,10"));
    }

    @Test
    void germanStringOfZeroAndNegative() {
        assertThat(BigDecimalTools.makeGermanString(BigDecimal.ZERO), is("0,00"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("0.00")), is("0,00"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("-1.5")), is("-1,50"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("-1234.567")), is("-1234,57"));
    }

    @Test
    void germanStringHasNoThousandsSeparator() {
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("1234567.891")), is("1234567,89"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("1000")), is("1000,00"));
    }

    @Test
    void germanStringRoundsHalfEven() {
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("0.125")), is("0,12"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("0.135")), is("0,14"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("2.675")), is("2,68"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("0.1251")), is("0,13"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("-0.125")), is("-0,12"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("0.004")), is("0,00"));
    }

    @Test
    void germanStringOfNullIsEmpty() {
        assertThat(BigDecimalTools.makeGermanString(null), is(emptyString()));
        assertThat(BigDecimalTools.makeGermanString(null, "€"), is(emptyString()));
    }

    @Test
    void germanStringWithUnitAppendsUnitAfterBlank() {
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("19.9"), "€"), is("19,90 €"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("20"), "%"), is("20,00 %"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("-3.333"), "ct"), is("-3,33 ct"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("0.125"), "ct"), is("0,12 ct"));
    }

    @Test
    void germanStringWithoutUnitHasNoTrailingBlank() {
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("1.5"), null), is("1,50"));
        assertThat(BigDecimalTools.makeGermanString(new BigDecimal("1.5"), ""), is("1,50"));
    }
}
