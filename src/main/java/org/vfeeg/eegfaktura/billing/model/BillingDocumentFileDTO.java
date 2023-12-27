package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;


@Getter
@Setter
public class BillingDocumentFileDTO {

    private UUID id;
    private String name;
    private String mimeType;
    private String tenantId;
    private UUID fileDataId;
    private UUID billingDocumentId;
    private String participantId;

}
