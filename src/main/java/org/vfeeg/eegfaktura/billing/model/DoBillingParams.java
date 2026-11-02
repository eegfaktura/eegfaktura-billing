package org.vfeeg.eegfaktura.billing.model;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
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
    /**
     * Required (known-errors #34, decided 2026-10-29): without the key - or misspelt, e.g. {@code isPreview} -
     * the request is refused with 400; before, it silently ran as a final run.
     */
    @NotNull
    @Getter(AccessLevel.NONE)
    private Boolean preview;
    private BillingConfig billingConfig;

    public boolean isPreview() {
        return Boolean.TRUE.equals(preview);
    }

}
