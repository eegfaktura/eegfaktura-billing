package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One member of a community in {@code GET /api/billingDocuments/tenant/{tenantId}/joiningFeeState}: members with at
 * least one final document (run DONE) or with a joining-fee record (charge requested, paid externally; extension
 * 27.10.2026). Read by eegfaktura-web (hint at the tariff assignment, warning in the tariff dialog, member export,
 * status in the member details).
 */
@Getter
@Setter
public class JoiningFeeState {

    private String participantId;
    /** A final document of this community carries an energy item for the member. */
    private boolean energyBilled;
    /** The final document with the joining fee, {@code null} when none was billed. */
    private Document joiningFee;
    /** Charge requested for the next final run and not billed yet. */
    private boolean requested;
    /** Recorded as paid outside eegfaktura, {@code null} otherwise. */
    private PaidExternally paidExternally;

    @Getter
    @Setter
    public static class PaidExternally {
        private LocalDate paidOn;
        private String note;
    }

    @Getter
    @Setter
    public static class Document {
        private String documentNumber;
        private LocalDate documentDate;
    }
}
