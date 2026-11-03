package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentNumberDTO;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentNumberService;


@RestController
@RequestMapping(value = "/api/billingDocumentNumbers", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingDocumentNumberResource {

    private final BillingDocumentNumberService billingDocumentNumberService;

    public BillingDocumentNumberResource(
            final BillingDocumentNumberService billingDocumentNumberService) {
        this.billingDocumentNumberService = billingDocumentNumberService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingDocumentNumberDTO> getBillingDocumentNumber(
            @PathVariable(name = "id") final UUID id) {
        BillingDocumentNumberDTO billingDocumentNumberDTO = billingDocumentNumberService.get(id);
        TenantContext.validateTenant(billingDocumentNumberDTO.getTenantId());
        return ResponseEntity.ok(billingDocumentNumberDTO);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteBillingDocumentNumber(
            @PathVariable(name = "id") final UUID id) {
        BillingDocumentNumberDTO billingDocumentNumberDTO = billingDocumentNumberService.get(id);
        TenantContext.validateTenant(billingDocumentNumberDTO.getTenantId());
        billingDocumentNumberService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
