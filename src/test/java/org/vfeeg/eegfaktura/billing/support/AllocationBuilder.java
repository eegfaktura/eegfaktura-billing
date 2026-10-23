package org.vfeeg.eegfaktura.billing.support;

import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.AllocationBucket;
import org.vfeeg.eegfaktura.billing.model.AllocationTimeWindow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Builds an {@link Allocation}; the kWh value is parsed from text, never from a {@code double}. */
public final class AllocationBuilder {

    private String participantId;
    private String meteringPoint;
    private BigDecimal kwh = BigDecimal.ZERO;
    private final List<AllocationBucket> buckets = new ArrayList<>();
    private final List<AllocationTimeWindow> windows = new ArrayList<>();

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

    /** A window share of the energy (ZVT): {@code BASE}, {@code T1} or {@code T2}. */
    public AllocationBuilder bucket(String key, String kwh) {
        AllocationBucket bucket = new AllocationBucket();
        bucket.setKey(key);
        bucket.setKWh(new BigDecimal(kwh));
        buckets.add(bucket);
        return this;
    }

    /** The window definition the caller used (ZVT), checked against the tariff. */
    public AllocationBuilder window(String key, String from, String to) {
        AllocationTimeWindow window = new AllocationTimeWindow();
        window.setKey(key);
        window.setFrom(from);
        window.setTo(to);
        windows.add(window);
        return this;
    }

    public Allocation build() {
        Allocation allocation = new Allocation();
        allocation.setParticipantId(participantId);
        allocation.setMeteringPoint(meteringPoint);
        allocation.setAllocationKWh(kwh);
        if (!buckets.isEmpty()) {
            allocation.setBuckets(buckets.toArray(new AllocationBucket[0]));
        }
        if (!windows.isEmpty()) {
            allocation.setTimeWindows(windows.toArray(new AllocationTimeWindow[0]));
        }
        return allocation;
    }
}
