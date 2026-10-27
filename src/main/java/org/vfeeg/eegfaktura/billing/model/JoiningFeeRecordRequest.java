package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of {@code PUT /api/billingDocuments/tenant/{tenantId}/participants/{participantId}/joiningFee}:
 * {@code {"kind": "CHARGE_REQUESTED"}} or {@code {"kind": "PAID_EXTERNALLY", "paidOn": "YYYY-MM-DD", "note": "…"}}.
 * Texts, not enum/date types: a wrong value is answered with 400 by {@code JoiningFeeService}, not 500.
 */
@Getter
@Setter
public class JoiningFeeRecordRequest {
    private String kind;
    private String paidOn;
    private String note;
}
