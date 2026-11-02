package org.vfeeg.eegfaktura.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.vfeeg.eegfaktura.billing.util.ClearingPeriodMonths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ClearingPeriodMonthsTests {

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource({
            "Abr_YM-2025-3,  Abr_YQ-2025-1,  true",
            "Abr_YM-2025-4,  Abr_YQ-2025-1,  false",
            "Abr_YM-2025-6,  Abr_YH-2025-1,  true",
            "Abr_YM-2025-7,  Abr_YH-2025-1,  false",
            "Abr_YQ-2025-3,  Abr_YH-2025-2,  true",
            "Abr_YQ-2025-2,  Abr_YH-2025-2,  false",
            "Abr_Y-2025-0,   Abr_YM-2025-12, true",
            "Abr_Y-2025,     Abr_YQ-2025-4,  true",
            "Abr_Y-2025-0,   Abr_YM-2026-1,  false",
            "Abr_YM-2025-12, Abr_YM-2026-12, false",
    })
    void comparesTheMonthsOfTwoPeriods(String first, String second, boolean overlap) {
        ClearingPeriodMonths a = ClearingPeriodMonths.of(first).orElseThrow();
        ClearingPeriodMonths b = ClearingPeriodMonths.of(second).orElseThrow();

        assertThat(a.overlaps(b), is(overlap));
        assertThat(b.overlaps(a), is(overlap));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Abr_YQ_2024-3", "Abr_YQ-2024", "Abr_YQ-2024-5", "Abr_YH-2024-3", "Abr_YM-2024-0",
            "Abr_YM-2024-13", "Abr_X-2024-1", "YQ-2024-1"})
    void anIdentifierOfAnotherFormCannotBeCompared(String identifier) {
        assertThat(ClearingPeriodMonths.of(identifier).isEmpty(), is(true));
    }

    @Test
    void noIdentifierCannotBeCompared() {
        assertThat(ClearingPeriodMonths.of(null).isEmpty(), is(true));
    }
}
