package org.vfeeg.eegfaktura.billing.support;

import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.service.BillingService;

/**
 * A billing run the way production starts one (BillingResource): accept, claim (RUNNING), execute - each step in
 * its own transaction, synchronously instead of on the executor. Exceptions of the calculation propagate; in
 * production the launcher would mark the run FAILED (covered by AsyncBillingRunTests).
 */
public final class BillingRuns {

    private BillingRuns() {
    }

    public static DoBillingResults bill(BillingService billingService, DoBillingParams params) {
        BillingRun billingRun = billingService.acceptBillingRun(params);
        if (!billingService.claimBillingRun(billingRun.getId())) {
            throw new IllegalStateException("claim lost: run " + billingRun.getId() + " is already RUNNING");
        }
        return billingService.executeBillingRun(billingRun.getId(), params);
    }
}
