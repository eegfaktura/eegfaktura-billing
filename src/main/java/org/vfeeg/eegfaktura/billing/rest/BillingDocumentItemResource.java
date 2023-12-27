package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentItemDTO;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentFileService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentItemService;


@RestController
@RequestMapping(value = "/api/billingDocumentItems", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingDocumentItemResource {

    private final BillingDocumentItemService billingDocumentItemService;

    public BillingDocumentItemResource(
            final BillingDocumentItemService billingDocumentItemService) {
        this.billingDocumentItemService = billingDocumentItemService;
    }

//    @GetMapping
//    public ResponseEntity<List<BillingDocumentItemDTO>> getAllBillingDocumentItems() {
//        return ResponseEntity.ok(billingDocumentItemService.findAll());
//    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingDocumentItemDTO> getBillingDocumentItem(
            @PathVariable(name = "id") final UUID id) {
        BillingDocumentItemDTO billingDocumentItemDTO = billingDocumentItemService.get(id);
        TenantContext.validateTenant(billingDocumentItemDTO.getTenantId());
        return ResponseEntity.ok(billingDocumentItemDTO);
    }

//    @PostMapping
//    @ApiResponse(responseCode = "201")
//    public ResponseEntity<UUID> createBillingDocumentItem(
//            @RequestBody @Valid final BillingDocumentItemDTO billingDocumentItemDTO) {
//        final UUID createdId = billingDocumentItemService.create(billingDocumentItemDTO);
//        return new ResponseEntity<>(createdId, HttpStatus.CREATED);
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<Void> updateBillingDocumentItem(@PathVariable(name = "id") final UUID id,
//            @RequestBody @Valid final BillingDocumentItemDTO billingDocumentItemDTO) {
//        billingDocumentItemService.update(id, billingDocumentItemDTO);
//        return ResponseEntity.ok().build();
//    }

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
