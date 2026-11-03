package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentFileDTO;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentFileService;


@RestController
@RequestMapping(value = "/api/billingDocumentFiles", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingDocumentFileResource {

    private final BillingDocumentFileService billingDocumentFileService;

    public BillingDocumentFileResource(final BillingDocumentFileService billingDocumentFileService) {
        this.billingDocumentFileService = billingDocumentFileService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingDocumentFileDTO> getFile(@PathVariable(name = "id") final UUID id) {
        BillingDocumentFileDTO billingDocumentFileDTO = billingDocumentFileService.get(id);
        TenantContext.validateTenant(billingDocumentFileDTO.getTenantId());
        return ResponseEntity.ok(billingDocumentFileDTO);
    }

    @GetMapping("/tenant/{id}")
    public ResponseEntity<List<BillingDocumentFileDTO>> getFilesByTenant(@PathVariable(name = "id") final String tenantId) {
        TenantContext.validateTenant(tenantId);
        return ResponseEntity.ok(billingDocumentFileService.findByTenantId(tenantId));
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteFile(@PathVariable(name = "id") final UUID id) {
        BillingDocumentFileDTO billingDocumentFileDTO = billingDocumentFileService.get(id);
        TenantContext.validateTenant(billingDocumentFileDTO.getTenantId());
        billingDocumentFileService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
