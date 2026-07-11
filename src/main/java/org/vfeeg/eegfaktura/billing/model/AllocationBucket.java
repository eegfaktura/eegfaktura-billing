package org.vfeeg.eegfaktura.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Fenster-Teilsumme eines Zaehlpunkts aus dem energystore-Report (ZVT).
 * key: BASE (Residuum) | T1 | T2; kWh: Menge des Buckets.
 */
@Getter
@Setter
public class AllocationBucket {
    private String key;
    @JsonProperty("kWh")
    private BigDecimal kWh;
}
