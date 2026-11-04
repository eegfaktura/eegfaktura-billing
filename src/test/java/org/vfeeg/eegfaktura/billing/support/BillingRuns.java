package org.vfeeg.eegfaktura.billing.support;

import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.service.BillingService;

/**
 * The steps BillingResource takes for a billing run: accept, claim (RUNNING), execute - synchronously, without
 * BillingRunLauncher.launch (its executor and its catch-all that marks a failed run FAILED, covered by
 * AsyncBillingRunTests). Each step is its own transaction; in a {@code @Transactional} test class all three join the
 * test transaction. Exceptions of the calculation propagate.
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
