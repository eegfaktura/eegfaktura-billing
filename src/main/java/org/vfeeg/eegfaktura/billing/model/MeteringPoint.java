package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MeteringPoint {
    private String id;
    private BigDecimal amount = BigDecimal.valueOf(0);
}
