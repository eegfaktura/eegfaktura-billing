package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vfeeg.eegfaktura.billing.domain.BillingRun;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingRunLauncher;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;

@RestController
@RequestMapping(value = "/api/billing", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class BillingResource {

    private final BillingService billingService;
    private final BillingRunLauncher billingRunLauncher;

    public BillingResource(final BillingService billingService,
                           final BillingRunLauncher billingRunLauncher) {
        this.billingService = billingService;
        this.billingRunLauncher = billingRunLauncher;
    }

    /**
     * Startet den Abrechnungslauf asynchron (siehe konzept-async-billing-run.md):
     * 202 + billingRunId bei Annahme, 409 + billingRunId wenn fuer (Tenant,
     * Periode) bereits ein Lauf RUNNING ist, 503 wenn der Executor voll ist.
     * Ergebnis/Status via GET /api/billingRuns/{id} (Polling).
     */
    @PostMapping
    @ApiResponse(responseCode = "202")
    public ResponseEntity<DoBillingResults> startBilling(
            @RequestBody @Valid final DoBillingParams doBillingParams) {
        TenantContext.validateTenant(doBillingParams.getTenantId());

        BillingRun billingRun;
        try {
            billingRun = billingService.acceptBillingRun(doBillingParams);
        } catch (IllegalArgumentException e) {
            return respond(HttpStatus.BAD_REQUEST, null, e.getMessage());
        } catch (BillingService.BillingRunAlreadyClosedException e) {
            return respond(HttpStatus.CONFLICT, null, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // Erstanlage-Race: paralleler POST hat den Run soeben angelegt
            // (Unique-Index V1_15) -> als laufenden Lauf behandeln
            return respond(HttpStatus.CONFLICT, null,
                    "Abrechnungslauf wurde soeben von einem anderen Aufruf gestartet.");
        }

        BillingRunStatus statusBeforeClaim = billingRun.getRunStatus();
        if (!billingService.claimBillingRun(billingRun.getId())) {
            // Claim verloren: fuer (Tenant, Periode) laeuft bereits ein Lauf
            return respond(HttpStatus.CONFLICT, billingRun,
                    "Abrechnungslauf läuft bereits.");
        }

        try {
            billingRunLauncher.launch(billingRun.getId(), doBillingParams);
        } catch (TaskRejectedException e) {
            billingService.revertBillingRunClaim(billingRun.getId(), statusBeforeClaim);
            log.warn("Abrechnungslauf abgelehnt, Executor ausgelastet (billingRunId={})", billingRun.getId());
            return respond(HttpStatus.SERVICE_UNAVAILABLE, billingRun,
                    "System ausgelastet - bitte später erneut versuchen.");
        }

        return respond(HttpStatus.ACCEPTED, billingRun, "Abrechnungslauf gestartet.");
    }

    private ResponseEntity<DoBillingResults> respond(HttpStatus status, BillingRun billingRun, String text) {
        DoBillingResults results = new DoBillingResults();
        results.setBillingRunId(billingRun != null ? billingRun.getId() : null);
        results.setAbstractText(text);
        return ResponseEntity.status(status).body(results);
    }
}
