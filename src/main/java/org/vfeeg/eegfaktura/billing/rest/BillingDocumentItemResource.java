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
import org.vfeeg.eegfaktura.billing.model.BillingDocumentItemDTO;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentItemService;


@RestController
@RequestMapping(value = "/api/billingDocumentItems", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingDocumentItemResource {

    private final BillingDocumentItemService billingDocumentItemService;

    public BillingDocumentItemResource(
            final BillingDocumentItemService billingDocumentItemService) {
        this.billingDocumentItemService = billingDocumentItemService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingDocumentItemDTO> getBillingDocumentItem(
            @PathVariable(name = "id") final UUID id) {
        BillingDocumentItemDTO billingDocumentItemDTO = billingDocumentItemService.get(id);
        TenantContext.validateTenant(billingDocumentItemDTO.getTenantId());
        return ResponseEntity.ok(billingDocumentItemDTO);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteBillingDocumentItem(
            @PathVariable(name = "id") final UUID id) {
        BillingDocumentItemDTO billingDocumentItemDTO = billingDocumentItemService.get(id);
        TenantContext.validateTenant(billingDocumentItemDTO.getTenantId());
        billingDocumentItemService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
