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

    // ZVT: Fenster-Teilsummen (BASE/T1/T2) + verwendete Fenster-Definitionen
    // als Konsistenz-Guard. Nur bei zeitbasierten Tarifen befuellt.
    private AllocationBucket[] buckets;
    private AllocationTimeWindow[] timeWindows;
}
