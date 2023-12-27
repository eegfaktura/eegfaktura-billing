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

@RestController
@RequestMapping(value = "/api/billing", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingResource {

    private final BillingService billingService;

    public BillingResource(final BillingService billingService) {
        this.billingService = billingService;
    }
    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<DoBillingResults> getAllInvoices(
            @RequestBody @Valid final DoBillingParams doBillingParams) {
        TenantContext.validateTenant(doBillingParams.getTenantId());
        return ResponseEntity.ok(billingService.doBilling(doBillingParams));
    }
}
