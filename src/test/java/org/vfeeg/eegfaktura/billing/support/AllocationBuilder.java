package org.vfeeg.eegfaktura.billing.support;

import org.vfeeg.eegfaktura.billing.model.Allocation;

import java.math.BigDecimal;

/** Builds an {@link Allocation}; the kWh value is parsed from text, never from a {@code double}. */
public final class AllocationBuilder {

    private String participantId;
    private String meteringPoint;
    private BigDecimal kwh = BigDecimal.ZERO;

    private AllocationBuilder() {
    }

    public static AllocationBuilder allocation() {
        return new AllocationBuilder();
    }

    public static Allocation of(String participantId, String meteringPoint, String kwh) {
        return allocation().participant(participantId).meteringPoint(meteringPoint).kwh(kwh).build();
    }

    public AllocationBuilder participant(String participantId) {
        this.participantId = participantId;
        return this;
    }

    public AllocationBuilder meteringPoint(String meteringPoint) {
        this.meteringPoint = meteringPoint;
        return this;
    }

    public AllocationBuilder kwh(String kwh) {
        this.kwh = new BigDecimal(kwh);
        return this;
    }

    public Allocation build() {
        Allocation allocation = new Allocation();
        allocation.setParticipantId(participantId);
        allocation.setMeteringPoint(meteringPoint);
        allocation.setAllocationKWh(kwh);
        return allocation;
    }
}
