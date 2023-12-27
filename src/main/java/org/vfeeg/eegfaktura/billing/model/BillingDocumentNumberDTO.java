package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;

import java.util.UUID;


@Getter
@Setter
public class BillingDocumentNumberDTO {

    private UUID id;

    private String tenantId;

    private BillingDocumentType documentType;

    private int year;

    private String prefix;

    private Long sequenceNumber;

    private String documentNumber;

}
