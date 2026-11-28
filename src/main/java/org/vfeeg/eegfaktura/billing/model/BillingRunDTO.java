package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;

import java.time.LocalDateTime;
import java.util.UUID;


@Getter
@Setter
public class BillingRunDTO {

    private UUID id;

    private String clearingPeriodType;

    private String clearingPeriodIdentifier;

    private String tenantId;

    private BillingRunStatus runStatus;

    private LocalDateTime runStatusDateTime;

    private String errorSummary;

    private String mailStatus;

    private String sendMailProtocol;

    private LocalDateTime mailStatusDateTime;

    private String sepaStatus;

    private LocalDateTime sepaStatusDateTime;

    private Integer numberOfInvoices;

    private Integer numberOfCreditNotes;

    /** The EEG's mail text could not be used, the last mail run sent the default text (from the protocol). */
    private boolean mailTemplateFallback;

}
