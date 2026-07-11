package org.vfeeg.eegfaktura.billing;

import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * billing_run.run_status ist smallint und wird ORDINAL gemappt - diese
 * Ordinals sind faktisch das Wire-Format in der Datenbank. Schlaegt dieser
 * Test fehl, wurden Enum-Werte umsortiert und ALLE Bestandslaeufe umgedeutet.
 */
class BillingRunStatusOrdinalTests {

    @Test
    void ordinalsAreStable() {
        assertThat(BillingRunStatus.NEW.ordinal(), equalTo(0));
        assertThat(BillingRunStatus.DONE.ordinal(), equalTo(1));
        assertThat(BillingRunStatus.CANCELLED.ordinal(), equalTo(2));
        assertThat(BillingRunStatus.RUNNING.ordinal(), equalTo(3));
        assertThat(BillingRunStatus.FAILED.ordinal(), equalTo(4));
        assertThat(BillingRunStatus.values().length, equalTo(5));
    }
}
