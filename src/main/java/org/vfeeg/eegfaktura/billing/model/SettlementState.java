package org.vfeeg.eegfaktura.billing.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * {@code GET /api/billingRuns/tenant/{tenantId}/settlementState}: the clearing periods of a community that decide
 * whether its settlement interval may change (konzept-abrechnungsintervall.md, platform#230). eegfaktura-web passes
 * it on to eegfaktura-backend, which applies the rule. Both lists without duplicates, never null, sorted as text, not
 * by time ({@code Abr_YM-2026-10} before {@code Abr_YM-2026-3}): callers must not rely on the order.
 */
@Getter
@Setter
public class SettlementState {

    /** Identifiers of the final runs (DONE) with at least one document: the runs the overlap check counts. */
    private List<String> finalPeriods = List.of();
    /** Identifiers of the runs being calculated (RUNNING), previews included. */
    private List<String> runningPeriods = List.of();
}
