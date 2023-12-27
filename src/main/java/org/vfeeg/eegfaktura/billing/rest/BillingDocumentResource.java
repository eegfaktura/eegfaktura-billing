package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping(value = "/api/billingDocuments", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingDocumentResource {

    private final BillingDocumentService billingDocumentService;

    public BillingDocumentResource(final BillingDocumentService billingDocumentService) {
        this.billingDocumentService = billingDocumentService;
    }

//    @GetMapping
//    public ResponseEntity<List<BillingDocumentDTO>> getAllBillingDocuments() {
//        return ResponseEntity.ok(billingDocumentService.findAll());
//    }

    @GetMapping("/tenant/{id}/{year}")
    public ResponseEntity<List<BillingDocumentDTO>> getBillingDocumentsByTenantIdAndYear(
            @PathVariable(name = "year") final int year,
            @PathVariable(name = "id") final String tenantId) {
        TenantContext.validateTenant(tenantId);
        return ResponseEntity.ok(billingDocumentService.findByTenantIdAndYear(tenantId, year));
    }

//    @GetMapping("/tenant/{id}/{year}/export?xlsx")
//    public ResponseEntity<List<BillingDocumentDTO>> exportBillingDocumentsByTenantIdAndYear(@PathVariable(name = "year") final String year,
//                                                                                  @PathVariable(name = "id") final String tenantId) {
//        return ResponseEntity.ok(billingDocumentService.findByTenantIdAndYear(year, tenantId));
//        //
//    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingDocumentDTO> getBillingDocument(
            @PathVariable(name = "id") final UUID id) {
        BillingDocumentDTO billingDocumentDTO = billingDocumentService.get(id);
        TenantContext.validateTenant(billingDocumentDTO.getTenantId());
        return ResponseEntity.ok(billingDocumentDTO);
    }

//    @PostMapping
//    @ApiResponse(responseCode = "201")
//    public ResponseEntity<UUID> createBillingDocument(
//            @RequestBody @Valid final BillingDocumentDTO billingDocumentDTO) {
//        final UUID createdId = billingDocumentService.create(billingDocumentDTO);
//        return new ResponseEntity<>(createdId, HttpStatus.CREATED);
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<Void> updateBillingDocument(@PathVariable(name = "id") final UUID id,
//            @RequestBody @Valid final BillingDocumentDTO billingDocumentDTO) {
//        billingDocumentService.update(id, billingDocumentDTO);
//        return ResponseEntity.ok().build();
//    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteBillingDocument(@PathVariable(name = "id") final UUID id) {
        BillingDocumentDTO billingDocumentDTO = billingDocumentService.get(id);
        TenantContext.validateTenant(billingDocumentDTO.getTenantId());
        billingDocumentService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
