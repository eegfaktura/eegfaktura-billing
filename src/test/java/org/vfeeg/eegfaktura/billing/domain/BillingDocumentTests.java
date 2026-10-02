package org.vfeeg.eegfaktura.billing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;

/** The type name is printed in the mail subject and body ("Rechnung Abr_YQ-2024-3"). */
class BillingDocumentTests {

    @Test
    void namesOfEveryDocumentType() {
        assertThat(BillingDocument.getDocumentTypeName(BillingDocumentType.INVOICE), is("Rechnung"));
        assertThat(BillingDocument.getDocumentTypeName(BillingDocumentType.CREDIT_NOTE), is("Gutschrift"));
        assertThat(BillingDocument.getDocumentTypeName(BillingDocumentType.CREDIT_NOTE_RC), is("Gutschrift"));
        assertThat(BillingDocument.getDocumentTypeName(BillingDocumentType.INFO), is("Information"));
    }

    /** Guard: a new enum value must get a deliberate name, not the generic fallback. */
    @ParameterizedTest
    @EnumSource(BillingDocumentType.class)
    void everyTypeHasASpecificName(BillingDocumentType type) {
        assertThat(BillingDocument.getDocumentTypeName(type), is(oneOf("Rechnung", "Gutschrift", "Information")));
    }
}
