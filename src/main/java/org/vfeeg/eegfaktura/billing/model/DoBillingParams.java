package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;

import java.time.LocalDate;

enum DoBillingType {
    PREVIEW, ACTUAL
}

@Getter
@Setter
public class DoBillingParams {

    private String clearingPeriodType;
    private String clearingPeriodIdentifier;
    private String tenantId;
    private LocalDate clearingDocumentDate;
    private Allocation[] allocations;
    private boolean isPreview;
    private BillingConfig billingConfig;

}
