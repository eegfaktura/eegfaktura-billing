package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeRecordRequest;
import org.vfeeg.eegfaktura.billing.model.JoiningFeeState;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.service.JoiningFeeService;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping(value = "/api/billingDocuments", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingDocumentResource {

    private final BillingDocumentService billingDocumentService;
    private final JoiningFeeService joiningFeeService;

    public BillingDocumentResource(final BillingDocumentService billingDocumentService,
                                   final JoiningFeeService joiningFeeService) {
        this.billingDocumentService = billingDocumentService;
        this.joiningFeeService = joiningFeeService;
    }

    @GetMapping("/tenant/{id}/{year}")
    public ResponseEntity<List<BillingDocumentDTO>> getBillingDocumentsByTenantIdAndYear(
            @PathVariable(name = "year") final int year,
            @PathVariable(name = "id") final String tenantId) {
        TenantContext.validateTenant(tenantId);
        return ResponseEntity.ok(billingDocumentService.findByTenantIdAndYear(tenantId, year));
    }

    /** Joining fee per member of the community (final runs only); eegfaktura-web, konzept-beitrittsgebuehr.md. */
    @GetMapping("/tenant/{tenantId}/joiningFeeState")
    public ResponseEntity<List<JoiningFeeState>> getJoiningFeeState(
            @PathVariable(name = "tenantId") final String tenantId) {
        TenantContext.validateTenant(tenantId);
        return ResponseEntity.ok(joiningFeeService.findJoiningFeeState(tenantId));
    }

    /**
     * Records „charge in the next final run“ or „paid outside eegfaktura“ for a member (extension 27.10.2026);
     * 409 once a final document with the joining fee exists, 400 for an invalid body.
     */
    @PutMapping(value = "/tenant/{tenantId}/participants/{participantId}/joiningFee",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> saveJoiningFeeRecord(@PathVariable(name = "tenantId") final String tenantId,
                                                     @PathVariable(name = "participantId") final String participantId,
                                                     @RequestBody(required = false) final JoiningFeeRecordRequest request,
                                                     final Authentication authentication) {
        TenantContext.validateTenant(tenantId);
        joiningFeeService.saveRecord(tenantId, participantId, request,
                authentication == null ? null : authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** Takes the record back (idempotent); 409 once a final document with the joining fee exists. */
    @DeleteMapping("/tenant/{tenantId}/participants/{participantId}/joiningFee")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteJoiningFeeRecord(@PathVariable(name = "tenantId") final String tenantId,
                                                       @PathVariable(name = "participantId") final String participantId) {
        TenantContext.validateTenant(tenantId);
        joiningFeeService.deleteRecord(tenantId, participantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingDocumentDTO> getBillingDocument(
            @PathVariable(name = "id") final UUID id) {
        BillingDocumentDTO billingDocumentDTO = billingDocumentService.get(id);
        TenantContext.validateTenant(billingDocumentDTO.getTenantId());
        return ResponseEntity.ok(billingDocumentDTO);
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteBillingDocument(@PathVariable(name = "id") final UUID id) {
        BillingDocumentDTO billingDocumentDTO = billingDocumentService.get(id);
        TenantContext.validateTenant(billingDocumentDTO.getTenantId());
        billingDocumentService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
