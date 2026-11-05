package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * {@code GET /api/billingRuns/tenant/{tenantId}/settlementState}: the clearing periods of a community that decide
 * whether its settlement interval may change (konzept-abrechnungsintervall.md, platform#230). eegfaktura-web passes
 * it on to eegfaktura-backend, which applies the rule. Both lists without duplicates, sorted, never null.
 */
@Getter
@Setter
public class SettlementState {

    /** Identifiers of the final runs (DONE) with at least one document: the runs the overlap check counts. */
    private List<String> finalPeriods = List.of();
    /** Identifiers of the runs being calculated (RUNNING), previews included. */
    private List<String> runningPeriods = List.of();
}
