package org.vfeeg.eegfaktura.billing;

import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.util.ClearingPeriodIdentifierTool;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class ClearingPeriodIdentifierToolTests {

    @Test
    public void testInvalidIdentifiers() {
        assertThat(ClearingPeriodIdentifierTool.asText(null), is(nullValue()));
        assertThat(ClearingPeriodIdentifierTool.asText(""), is(emptyString()));
        assertThat(ClearingPeriodIdentifierTool.asText("-----"), is("-----"));
        assertThat(ClearingPeriodIdentifierTool.asText("1-2-3-4-5-6"), is("1-2-3-4-5-6"));
        assertThat(ClearingPeriodIdentifierTool.asText("-"), is("-"));
        assertThat(ClearingPeriodIdentifierTool.asText("Foo_Y_bar-holly"), is("Foo_Y_bar-holly"));
        assertThat(ClearingPeriodIdentifierTool.asText("1"), is("1"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_Y_ABCD-1"), is("Abr_Y_ABCD-1"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YH_2024-0"), is("Abr_YH_2024-0"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YH_2024-3"), is("Abr_YH_2024-3"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YH_2024-X"), is("Abr_YH_2024-X"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024"), is("Abr_YQ_2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-X"), is("Abr_YQ_2024-X"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-0"), is("Abr_YQ_2024-0"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-5"), is("Abr_YQ_2024-5"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-11"), is("Abr_YQ_2024-11"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024"), is("Abr_YM_2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_ABCD-1"), is("Abr_YM_ABCD-1"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-X"), is("Abr_YM_2024-X"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-0"), is("Abr_YM_2024-0"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-13"), is("Abr_YM_2024-13"));
    }

    @Test
    public void testY() {
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_Y_2023"), is("01.01.2023 - 31.12.2023"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_Y_2024"), is("01.01.2024 - 31.12.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_Y_2024-foobar"), is("01.01.2024 - 31.12.2024"));
    }

    @Test
    public void testYH() {
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YH_2024-1"), is("01.01.2024 - 30.06.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YH_2024-2"), is("01.07.2024 - 31.12.2024"));
    }

    @Test
    public void testYQ() {
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-1"), is("01.01.2024 - 31.03.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-2"), is("01.04.2024 - 30.06.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-3"), is("01.07.2024 - 30.09.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YQ_2024-4"), is("01.10.2024 - 31.12.2024"));
    }

    @Test
    public void testYM() {
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-1"), is("01.01.2024 - 31.01.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-2"), is("01.02.2024 - 29.02.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-3"), is("01.03.2024 - 31.03.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-4"), is("01.04.2024 - 30.04.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-5"), is("01.05.2024 - 31.05.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-6"), is("01.06.2024 - 30.06.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-7"), is("01.07.2024 - 31.07.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-8"), is("01.08.2024 - 31.08.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-9"), is("01.09.2024 - 30.09.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-10"), is("01.10.2024 - 31.10.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-11"), is("01.11.2024 - 30.11.2024"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2024-12"), is("01.12.2024 - 31.12.2024"));

        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2000-2"), is("01.02.2000 - 29.02.2000"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2023-2"), is("01.02.2023 - 28.02.2023"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2028-2"), is("01.02.2028 - 29.02.2028"));
        assertThat(ClearingPeriodIdentifierTool.asText("Abr_YM_2100-2"), is("01.02.2100 - 28.02.2100"));
    }


}
