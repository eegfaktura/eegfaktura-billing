package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ParticipantAmount {

    private UUID id;
    private BigDecimal participantFee = BigDecimal.ZERO;
    private BigDecimal meteringPointFeeSum = BigDecimal.ZERO;
    private BigDecimal amount = BigDecimal.ZERO;
    private List<MeteringPoint> meteringPoints = new ArrayList<>();
    private List<BillingDocumentFileDTO> billingDocumentFileDTOs = new ArrayList<>();

}
