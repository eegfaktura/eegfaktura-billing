package org.vfeeg.eegfaktura.billing.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    // "kwh" was read until Boot 3.5; without the alias it would be dropped and billed as 0 kWh without an error
    @JsonProperty("kWh")
    @JsonAlias("kwh")
    private BigDecimal kWh;
}
