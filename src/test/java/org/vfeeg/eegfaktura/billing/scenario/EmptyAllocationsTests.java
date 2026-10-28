package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.rest.BillingResource;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * platform#189: a run without energy amounts is refused at the start (400, no run row); a run whose amounts match no
 * member/metering point of the master data ends FAILED instead of DONE; one matching amount bills as before.
 */
class EmptyAllocationsTests extends BillingScenarioBase {

    private static final String NO_ALLOCATIONS = "Keine Energiemengen für die Abrechnung übergeben – bitte die Seite"
            + " neu laden und die Energiedaten des Zeitraums laden.";
    private static final String NOTHING_MATCHES = RESULT_FAILED + "Keine der übergebenen Energiemengen passt zu einem"
            + " Mitglied/Zählpunkt der Stammdaten – es wurde kein Beleg erstellt.";

    @Autowired
    private BillingResource billingResource;
    @Autowired
    private BillingRunRepository billingRunRepository;

    @BeforeEach
    void requestTenant() {
        TenantContext.setCurrentTenant(new Authority(tenant));
    }

    @AfterEach
    void clearRequestTenant() {
        TenantContext.setCurrentTenant(null);
    }

    private DoBillingParams params(BillingRunFixture world, boolean preview, Allocation[] allocations) {
        DoBillingParams params = world.params(PERIOD_TYPE, PERIOD, preview);
        params.setClearingDocumentDate(REFERENCE_DATE);
        params.setAllocations(allocations);
        return params;
    }

    /** Accept, claim and calculate as the start endpoint does, the calculation in this thread. */
    private BillingRun launch(DoBillingParams params) {
        BillingRun run = billingService.acceptBillingRun(params);
        assertThat(billingService.claimBillingRun(run.getId()), is(true));
        new BillingRunLauncher(billingService).launch(run.getId(), params);
        return billingRunRepository.findById(run.getId()).orElseThrow();
    }

    @ParameterizedTest(name = "preview={0}")
    @ValueSource(booleans = {true, false})
    void emptyAllocationsAreRefusedWithoutARun(boolean preview) {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(
                params(world, preview, new Allocation[0]));

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().getAbstractText(), is(NO_ALLOCATIONS));
        assertThat(response.getBody().getBillingRunId(), nullValue());
        assertThat(count("billing_run where tenant_id = ?"), is(0));
    }

    @ParameterizedTest(name = "preview={0}")
    @ValueSource(booleans = {true, false})
    void missingAllocationsAreRefusedWithoutARun(boolean preview) {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(params(world, preview, null));

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().getAbstractText(), is(NO_ALLOCATIONS));
        assertThat(count("billing_run where tenant_id = ?"), is(0));
    }

    @ParameterizedTest(name = "preview={0}")
    @ValueSource(booleans = {true, false})
    void allocationsMatchingNoMemberEndFailed(boolean preview) {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        Allocation[] unknown = {
                AllocationBuilder.of(participant(9), "C9", "100"), // unknown member
                AllocationBuilder.of(participant(1), "C9", "100")  // known member, unknown metering point
        };

        BillingRun run = launch(params(world, preview, unknown));

        assertThat(run.getRunStatus(), is(BillingRunStatus.FAILED));
        assertThat(run.getErrorSummary(), is(NOTHING_MATCHES));
        assertThat(count("billing_document where tenant_id = ?"), is(0));
    }

    @ParameterizedTest(name = "preview={0}")
    @ValueSource(booleans = {true, false})
    void oneMatchingAllocationBillsAsBefore(boolean preview) {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        Allocation[] mixed = {
                AllocationBuilder.of(participant(1), "C1", "100"),
                AllocationBuilder.of(participant(9), "C9", "100")
        };

        BillingRun run = launch(params(world, preview, mixed));

        assertThat(run.getRunStatus(), is(preview ? BillingRunStatus.NEW : BillingRunStatus.DONE));
        assertThat(run.getErrorSummary(), nullValue());
        assertThat(documents(run.getId()).size(), is(1));
    }
}
