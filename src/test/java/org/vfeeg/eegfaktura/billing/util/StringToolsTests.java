package org.vfeeg.eegfaktura.billing.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;

class StringToolsTests {

    @Test
    void joinsNonEmptyPartsWithSeparator() {
        assertThat(StringTools.nullSafeJoin(", ", "Hauptstraße 1", "1010 Wien"), is("Hauptstraße 1, 1010 Wien"));
        assertThat(StringTools.nullSafeJoin(" ", "single"), is("single"));
    }

    @Test
    void nullAndBlankPartsAreSkipped() {
        assertThat(StringTools.nullSafeJoin(", ", null, "a", "", "  ", null, "b"), is("a, b"));
        assertThat(StringTools.nullSafeJoin("-", "a", null), is("a"));
    }

    @Test
    void keepsInnerWhitespaceOfKeptParts() {
        assertThat(StringTools.nullSafeJoin("|", " a ", "b"), is(" a |b"));
    }

    @Test
    void onlyNullOrEmptyPartsGiveEmptyString() {
        assertThat(StringTools.nullSafeJoin(", "), is(emptyString()));
        assertThat(StringTools.nullSafeJoin(", ", (String) null), is(emptyString()));
        assertThat(StringTools.nullSafeJoin(", ", null, "", " "), is(emptyString()));
    }
}
