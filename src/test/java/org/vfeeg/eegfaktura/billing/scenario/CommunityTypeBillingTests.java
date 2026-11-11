package org.vfeeg.eegfaktura.billing.scenario;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.logging.LogFields;
import org.vfeeg.eegfaktura.billing.model.Allocation;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.rest.BillingResource;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.support.AllocationBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingRunFixture;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * platform#229, platform#255: peer-to-peer (P2P) and self-supply (EVA) communities get a preview only. A final run
 * for such a tenant (type from {@code base.billing_masterdata.eeg_area}) is refused at the start with 400 and no run
 * row; a preview bills as for any community. The other community types bill as before.
 */
class CommunityTypeBillingTests extends BillingScenarioBase {

    private static final String PREVIEW_ONLY = "Für Peer-to-Peer und Eigenversorgung erstellt eegfaktura nur"
            + " eine Vorschau, keine endgültige Abrechnung.";

    @Autowired
    private BillingResource billingResource;
    @Autowired
    private BillingRunRepository billingRunRepository;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(BillingService.class);

    @BeforeEach
    void requestTenant() {
        TenantContext.setCurrentTenant(new Authority(tenant));
        MDC.put(LogFields.TENANT_ID, tenant); // LogContextFilter of the request
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void clearRequestTenant() {
        TenantContext.setCurrentTenant(null);
        serviceLogger.detachAppender(appender);
        MDC.remove(LogFields.TENANT_ID);
    }

    private BillingRunFixture community(String area) {
        BillingRunFixture world = insert(world().master(consumer(1, "C1"), "100"), false);
        jdbc.update("update base.billing_masterdata set eeg_area = ? where tenant_id = ?", area, tenant);
        return world;
    }

    private static DoBillingParams params(BillingRunFixture world, boolean preview) {
        DoBillingParams params = world.params(PERIOD_TYPE, PERIOD, preview);
        params.setClearingDocumentDate(REFERENCE_DATE);
        params.setAllocations(new Allocation[] {AllocationBuilder.of(participant(1), "C1", "100")});
        return params;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"P2P", "EVA"})
    void aFinalRunOfP2pAndEvaIsRefusedWithoutARun(String area) {
        BillingRunFixture world = community(area);

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(params(world, false));

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().getAbstractText(), is(PREVIEW_ONLY));
        assertThat(response.getBody().getBillingRunId(), nullValue());
        assertThat(count("billing_run where tenant_id = ?"), is(0));
        // eegfaktura-dev#56 #12: the refusal has a line of its own, not only the access line
        List<ILoggingEvent> refused = appender.list.stream()
                .filter(line -> line.getKeyValuePairs() != null && line.getKeyValuePairs().stream().anyMatch(
                        pair -> LogFields.EVENT_ACTION.equals(pair.key) && "billing.run.refused".equals(pair.value)))
                .toList();
        assertThat(refused.size(), is(1));
        assertThat(refused.get(0).getMDCPropertyMap().get(LogFields.TENANT_ID), is(tenant));
    }

    /** platform#255: P2P and EVA bill a preview like any community ("Vorlage-Rechnungen"). */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"P2P", "EVA"})
    void p2pAndEvaBillAPreview(String area) {
        billsOneDocument(community(area), true);
    }

    /** The request may carry the tenant in another case than the master data (upper case). */
    @Test
    void theTenantCaseOfTheRequestDoesNotMatter() {
        BillingRunFixture world = community("P2P");
        DoBillingParams params = params(world, false);
        params.setTenantId(tenant.toLowerCase());

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(params);

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().getAbstractText(), is(PREVIEW_ONLY));
        assertThat(count("billing_run where upper(tenant_id) = ?"), is(0));
    }

    /** A preview run from before the switch to P2P is not finalised either. */
    @Test
    void anExistingRunOfACommunitySwitchedToP2pIsRefused() {
        BillingRunFixture world = community("LOCAL");
        BillingRun earlier = billingService.acceptBillingRun(params(world, true));
        jdbc.update("update base.billing_masterdata set eeg_area = 'P2P' where tenant_id = ?", tenant);

        ResponseEntity<DoBillingResults> response = billingResource.startBilling(params(world, false));

        assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody().getAbstractText(), is(PREVIEW_ONLY));
        assertThat(count("billing_run where tenant_id = ?"), is(1));
        assertThat(billingRunRepository.findById(earlier.getId()).orElseThrow().getRunStatus(),
                is(BillingRunStatus.NEW));
    }

    @ParameterizedTest(name = "{0} preview={1}")
    @CsvSource({"LOCAL, true", "LOCAL, false", "GEA, true", "GEA, false", "REGIONAL, false", "BEG, false"})
    void otherCommunityTypesBillAsBefore(String area, boolean preview) {
        billsOneDocument(community(area), preview);
    }

    /** No base.eeg row for the tenant (LEFT JOIN in the view): eeg_area is NULL and the run bills as before. */
    @Test
    void aMissingCommunityTypeBillsAsBefore() {
        BillingRunFixture world = community("LOCAL");
        jdbc.update("update base.billing_masterdata set eeg_area = null where tenant_id = ?", tenant);
        billsOneDocument(world, false);
    }

    private void billsOneDocument(BillingRunFixture world, boolean preview) {
        DoBillingParams params = params(world, preview);

        BillingRun accepted = billingService.acceptBillingRun(params);
        assertThat(billingService.claimBillingRun(accepted.getId()), is(true));
        new BillingRunLauncher(billingService).launch(accepted.getId(), params);
        BillingRun run = billingRunRepository.findById(accepted.getId()).orElseThrow();

        assertThat(run.getRunStatus(), is(preview ? BillingRunStatus.NEW : BillingRunStatus.DONE));
        assertThat(run.getErrorSummary(), nullValue());
        assertThat(documents(run.getId()).size(), is(1));
    }
}
