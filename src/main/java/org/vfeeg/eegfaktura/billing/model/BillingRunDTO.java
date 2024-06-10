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

    private String mailStatus;

    private String sendMailProtocol;

    private LocalDateTime mailStatusDateTime;

    private String sepaStatus;

    private LocalDateTime sepaStatusDateTime;

    private Integer numberOfInvoices;

    private Integer numberOfCreditNotes;

}
