package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Vom Aufrufer (web) verwendetes Zeitfenster (ZVT) - dient als
 * Konsistenz-Guard gegen die live Masterdata-View (kein Tarif-Snapshot):
 * weicht das mitgesendete Fenster von der aktuellen Tarif-Definition ab,
 * bricht der Lauf ab.
 */
@Getter
@Setter
public class AllocationTimeWindow {
    private String key;  // T1 | T2
    private String from; // HH:MM
    private String to;   // HH:MM
}
