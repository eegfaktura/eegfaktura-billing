package org.vfeeg.eegfaktura.billing.domain;

/**
 * ACHTUNG: billing_run.run_status ist smallint und wird von JPA ORDINAL gemappt
 * (BillingRun.runStatus hat kein @Enumerated). Neue Werte duerfen daher NUR am
 * Ende ergaenzt werden - Umsortieren wuerde alle Bestandsdaten umdeuten.
 * CANCELLED wird derzeit nirgends gesetzt, bleibt aber als Ordinal-Platzhalter (2).
 */
public enum BillingRunStatus {
    NEW, DONE, CANCELLED, RUNNING, FAILED
}
