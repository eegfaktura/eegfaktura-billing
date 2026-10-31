package org.vfeeg.eegfaktura.billing.scenario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.time.LocalDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * eegfaktura-dev tracking billing 261031-mm5: a final run of March and one of the first quarter have different
 * identifiers, so the unique index let both through and the energy of March stood on two documents.
 */
class FinalRunOverlapTests extends BillingScenarioBase {

    private static final String MARCH = "Abr_YM-2024-3";
    private static final String APRIL = "Abr_YM-2024-4";
    private static final String FIRST_QUARTER = "Abr_YQ-2024-1";
    private static final String SECOND_QUARTER = "Abr_YQ-2024-2";

    @Autowired
    private BillingResource billingResource;
    @Autowired
    private BillingRunRepository billingRunRepository;

    private BillingRunFixture world;

    @BeforeEach
    void worldWithOneMember() {
        TenantContext.setCurrentTenant(new Authority(tenant));
        world = insert(world().master(consumer(1, "C1"), "100"), false);
    }

    @AfterEach
    void clearRequestTenant() {
        TenantContext.setCurrentTenant(null);
    }

    private DoBillingParams params(String period, boolean preview) {
        DoBillingParams params = world.params(PERIOD_TYPE, period, preview);
        params.setClearingDocumentDate(REFERENCE_DATE);
        params.setAllocations(new Allocation[]{AllocationBuilder.of(participant(1), "C1", "100")});
        return params;
    }

    private void billFinal(String period) {
        billingService.doBilling(params(period, false));
    }

    @Test
    void aFinalRunOverlappingAFinalRunIsRefusedWithoutARun() {
        billFinal(MARCH);

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(params(FIRST_QUARTER, false));

        assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(response.getBody().getAbstractText(), containsString("01.03.2024 - 31.03.2024"));
        assertThat(count("billing_run where tenant_id = ?"), is(1));
    }

    /** The unique index and the closed-run lookup see case; the run must still be refused. */
    @Test
    void theSamePeriodUnderAnotherSpellingOfTheTenantIsRefused() {
        billFinal(MARCH);
        DoBillingParams sameMonth = params(MARCH, false);
        sameMonth.setTenantId(tenant.toLowerCase());

        assertThrows(BillingService.BillingPeriodOverlapException.class,
                () -> billingService.acceptBillingRun(sameMonth));
    }

    @Test
    void aFinalRunWithoutAnIdentifierDoesNotBreakTheCheck() {
        BillingRun noIdentifier = new BillingRun();
        noIdentifier.setTenantId(tenant);
        noIdentifier.setClearingPeriodType(PERIOD_TYPE);
        noIdentifier.setRunStatus(BillingRunStatus.DONE);
        noIdentifier.setRunStatusDateTime(LocalDateTime.now());
        billingRunRepository.save(noIdentifier);

        billFinal(MARCH);

        assertThat(count("billing_document where tenant_id = ?") > 0, is(true));
    }

    @Test
    void aClosedRunKeepsItsAnswer() {
        billFinal(MARCH);

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(params(MARCH, false));

        assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
        assertThat(response.getBody().getAbstractText(), containsString("bereits abgeschlossen"));
    }

    @Test
    void aFinalMonthInsideAFinalQuarterIsRefused() {
        billFinal(FIRST_QUARTER);

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(params(MARCH, false));

        assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
    }

    @Test
    void aFinalRunNextToAFinalRunGoesThrough() {
        billFinal(MARCH);
        billFinal(APRIL);

        assertThat(count("billing_run where tenant_id = ?"), is(2));
    }

    @Test
    void aChangeOfTheIntervalBetweenPeriodsGoesThrough() {
        billFinal(FIRST_QUARTER);
        billFinal("Abr_YM-2024-4");
        billFinal("Abr_YM-2024-5");
        billFinal("Abr_YH-2024-2");

        assertThat(count("billing_run where tenant_id = ?"), is(4));
    }

    @Test
    void aPreviewIsNotRefusedAndDoesNotRefuse() {
        billFinal(MARCH);
        billingService.doBilling(params(FIRST_QUARTER, true));
        billingService.doBilling(params(SECOND_QUARTER, true));

        billFinal(SECOND_QUARTER);

        assertThat(count("billing_run where tenant_id = ?"), is(3));
    }

    @Test
    void aFinalRunWithoutDocumentsDoesNotRefuse() {
        BillingRun empty = new BillingRun();
        empty.setTenantId(tenant);
        empty.setClearingPeriodType(PERIOD_TYPE);
        empty.setClearingPeriodIdentifier("Abr_Y-2024-0");
        empty.setRunStatus(BillingRunStatus.DONE);
        empty.setRunStatusDateTime(LocalDateTime.now());
        billingRunRepository.save(empty);

        billFinal(MARCH);

        assertThat(count("billing_document where tenant_id = ?") > 0, is(true));
    }

    /** The check at the start can be passed by a parallel run: the calculation repeats it under the lock. */
    @Test
    void theCalculationChecksAgain() {
        BillingRun accepted = billingService.acceptBillingRun(params(FIRST_QUARTER, false));
        billFinal(MARCH);

        assertThrows(BillingService.BillingPeriodOverlapException.class,
                () -> billingService.executeBillingRun(accepted.getId(), params(FIRST_QUARTER, false)));
    }
}
