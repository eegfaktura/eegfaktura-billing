package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Allocation {
    private String participantId;
    private String meteringPoint;
    private BigDecimal allocationKWh;
}
