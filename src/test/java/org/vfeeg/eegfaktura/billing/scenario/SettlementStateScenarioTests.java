package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.SettlementState;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.time.LocalDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * {@code GET /api/billingRuns/tenant/{tenantId}/settlementState} on the database (platform#230): a final run with
 * documents is final, a DONE run without documents (platform#189), a preview and a failed run are not; a RUNNING run
 * is running. The tenant is compared case-insensitively, as the overlap check does.
 */
class SettlementStateScenarioTests extends BillingScenarioBase {

    @Autowired
    private BillingRunRepository billingRunRepository;

    @Test
    void finalAndRunningPeriodsOfTheCommunity() {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        billingService.doBilling(params(world, "Abr_YM-2024-3", false));
        billingService.doBilling(params(world, "Abr_YQ-2024-2", true));
        assertThat(count("billing_run where tenant_id = ? and clearing_period_identifier = 'Abr_YQ-2024-2'"
                + " and run_status = " + BillingRunStatus.NEW.ordinal()), is(1));
        save("Abr_YM-2024-4", BillingRunStatus.DONE);
        save("Abr_YM-2024-5", BillingRunStatus.FAILED);
        save("Abr_YM-2024-6", BillingRunStatus.RUNNING);

        SettlementState state = billingRunService.findSettlementState(tenant.toLowerCase());

        assertThat(state.getFinalPeriods(), contains("Abr_YM-2024-3"));
        assertThat(state.getRunningPeriods(), contains("Abr_YM-2024-6"));
    }

    @Test
    void aCommunityWithoutRunsHasEmptyLists() {
        SettlementState state = billingRunService.findSettlementState(tenant);

        assertThat(state.getFinalPeriods(), is(empty()));
        assertThat(state.getRunningPeriods(), is(empty()));
    }

    private DoBillingParams params(BillingRunFixture world, String period, boolean preview) {
        DoBillingParams params = world.params(PERIOD_TYPE, period, preview);
        params.setClearingDocumentDate(REFERENCE_DATE);
        params.setAllocations(new Allocation[]{AllocationBuilder.of(participant(1), "C1", "100")});
        return params;
    }

    private void save(String identifier, BillingRunStatus status) {
        BillingRun run = new BillingRun();
        run.setTenantId(tenant);
        run.setClearingPeriodType(PERIOD_TYPE);
        run.setClearingPeriodIdentifier(identifier);
        run.setRunStatus(status);
        run.setRunStatusDateTime(LocalDateTime.now());
        billingRunRepository.save(run);
    }
}
