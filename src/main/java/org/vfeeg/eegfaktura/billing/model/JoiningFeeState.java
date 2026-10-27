package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One member of a community in {@code GET /api/billingDocuments/tenant/{tenantId}/joiningFeeState}: only members
 * with at least one final document (run DONE) are listed. Read by eegfaktura-web (hint at the tariff assignment,
 * warning in the tariff dialog, member export).
 */
@Getter
@Setter
public class JoiningFeeState {

    private String participantId;
    /** A final document of this community carries an energy item for the member. */
    private boolean energyBilled;
    /** The final document with the joining fee, {@code null} when none was billed. */
    private Document joiningFee;

    @Getter
    @Setter
    public static class Document {
        private String documentNumber;
        private LocalDate documentDate;
    }
}
