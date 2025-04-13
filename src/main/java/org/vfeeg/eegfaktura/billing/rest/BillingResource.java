package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.repos.InMemoryLockRepository;

@RestController
@RequestMapping(value = "/api/billing", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingResource {

    private final BillingService billingService;
    private final InMemoryLockRepository inMemoryLockRepository;

    public BillingResource(final BillingService billingService, InMemoryLockRepository inMemoryLockService) {
        this.billingService = billingService;
        this.inMemoryLockRepository = inMemoryLockService;
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<DoBillingResults> getAllInvoices(
            @RequestBody @Valid final DoBillingParams doBillingParams) {
        TenantContext.validateTenant(doBillingParams.getTenantId());
        Object lock = inMemoryLockRepository.getLock(doBillingParams.getTenantId());
        synchronized (lock) {
            try {
                return ResponseEntity.ok(billingService.doBilling(doBillingParams));
            } finally {
                inMemoryLockRepository.releaseLock(doBillingParams.getTenantId());
            }
        }
    }
}
