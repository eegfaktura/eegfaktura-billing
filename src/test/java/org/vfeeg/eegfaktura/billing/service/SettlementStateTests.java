package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.SettlementState;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingRunRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link BillingRunService#findSettlementState}: final periods are DONE runs with documents (as the overlap check
 * counts them), running periods every RUNNING run; both without duplicates and sorted (platform#230). The statuses
 * on the database: {@code SettlementStateScenarioTests}.
 */
@ExtendWith(MockitoExtension.class)
class SettlementStateTests {

    private static final String TENANT = "RC100401";

    @Mock private BillingRunRepository billingRunRepository;
    @Mock private BillingDocumentRepository billingDocumentRepository;
    @Mock private BillingDocumentItemRepository billingDocumentItemRepository;
    @Mock private BillingDocumentFileRepository billingDocumentFileRepository;
    @Mock private FileDataRepository fileDataRepository;

    private BillingRunService service;

    @BeforeEach
    void setUp() {
        service = new BillingRunService(billingRunRepository, billingDocumentRepository,
                billingDocumentItemRepository, billingDocumentFileRepository, fileDataRepository);
        lenient().when(billingRunRepository.findByTenantIdIgnoreCaseAndRunStatus(any(), any())).thenReturn(List.of());
    }

    @Test
    void finalPeriodsAreDoneRunsWithDocumentsWithoutDuplicatesSorted() {
        BillingRun march = run("Abr_YM-2026-3", true);
        BillingRun marchAgain = run("Abr_YM-2026-3", true);
        BillingRun quarter = run("Abr_YQ-2025-4", true);
        BillingRun empty = run("Abr_YM-2026-4", false);
        when(billingRunRepository.findByTenantIdIgnoreCaseAndRunStatus(TENANT, BillingRunStatus.DONE))
                .thenReturn(List.of(quarter, march, empty, marchAgain));

        SettlementState state = service.findSettlementState(TENANT);

        assertThat(state.getFinalPeriods(), contains("Abr_YM-2026-3", "Abr_YQ-2025-4"));
        assertThat(state.getRunningPeriods(), empty());
    }

    /** A running calculation counts whatever it is (billing does not store whether it is a preview). */
    @Test
    void runningPeriodsAreListedEvenWithoutDocuments() {
        BillingRun running = run("Abr_YM-2026-4", false);
        when(billingRunRepository.findByTenantIdIgnoreCaseAndRunStatus(TENANT, BillingRunStatus.RUNNING))
                .thenReturn(List.of(running));

        SettlementState state = service.findSettlementState(TENANT);

        assertThat(state.getRunningPeriods(), contains("Abr_YM-2026-4"));
        assertThat(state.getFinalPeriods(), empty());
    }

    @Test
    void aRunWithoutIdentifierIsLeftOut() {
        BillingRun unnamed = run(null, true);
        when(billingRunRepository.findByTenantIdIgnoreCaseAndRunStatus(TENANT, BillingRunStatus.DONE))
                .thenReturn(List.of(unnamed));

        assertThat(service.findSettlementState(TENANT).getFinalPeriods(), empty());
    }

    private BillingRun run(String identifier, boolean withDocuments) {
        BillingRun run = new BillingRun();
        run.setId(UUID.randomUUID());
        run.setClearingPeriodIdentifier(identifier);
        lenient().when(billingDocumentRepository.existsByBillingRunId(run.getId())).thenReturn(withDocuments);
        return run;
    }
}
