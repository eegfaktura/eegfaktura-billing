package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.ParticipantAmount;
import org.vfeeg.eegfaktura.billing.repos.InMemoryLockRepository;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/billingRuns", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class BillingRunResource {

    private final BillingRunService billingRunService;
    private final BillingDocumentService billingDocumentService;
    private final BillingDocumentXlsxService billingDocumentXlsxService;
    private final BillingDocumentArchiveService billingDocumentArchiveService;
    private final BillingDocumentMailService billingDocumentMailService;
    private final ParticipantAmountService participantAmountService;
    private final InMemoryLockRepository inMemoryLockRepository;

    public BillingRunResource(final BillingRunService billingRunService,
                              final BillingDocumentService billingDocumentService,
                              final BillingDocumentXlsxService billingDocumentXlsxService,
                              final BillingDocumentArchiveService billingDocumentArchiveService,
                              final BillingDocumentMailService billingDocumentMailService,
                              final ParticipantAmountService participantAmountService,
                              final InMemoryLockRepository inMemoryLockRepository
    ) {
        this.billingRunService = billingRunService;
        this.billingDocumentService = billingDocumentService;
        this.billingDocumentXlsxService = billingDocumentXlsxService;
        this.billingDocumentArchiveService = billingDocumentArchiveService;
        this.billingDocumentMailService = billingDocumentMailService;
        this.participantAmountService = participantAmountService;
        this.inMemoryLockRepository = inMemoryLockRepository;
    }

//    @GetMapping
//    public ResponseEntity<List<BillingRunDTO>> getAllBillingRuns() {
//        return ResponseEntity.ok(billingRunService.findAll());
//    }

    @GetMapping("/{tenantId}/{clearingPeriodType}/{clearingPeriodIdentifier}")
    public ResponseEntity<List<BillingRunDTO>> getAllBillingRuns(
            @PathVariable(name = "tenantId") final String tenantId,
            @PathVariable(name = "clearingPeriodType") final String clearingPeriodType,
            @PathVariable(name = "clearingPeriodIdentifier") final String clearingPeriodIdentifier) {
        TenantContext.validateTenant(tenantId);
        return ResponseEntity.ok(billingRunService.findByTenantIdAndClearingPeriodTypeAndClearingPeriodIdentifier(
                tenantId, clearingPeriodType, clearingPeriodIdentifier
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingRunDTO> getBillingRun(@PathVariable(name = "id") final UUID id) {
        BillingRunDTO billingRunDTO = billingRunService.get(id);
        TenantContext.validateTenant(billingRunDTO.getTenantId());
        return ResponseEntity.ok(billingRunDTO);
    }

    @GetMapping("/{id}/participantAmounts")
    public ResponseEntity<List<ParticipantAmount>> getParticipantAmountsByBillingRunId(
            @PathVariable(name = "id") final UUID id) {
        BillingRunDTO billingRunDTO = billingRunService.get(id);
        TenantContext.validateTenant(billingRunDTO.getTenantId());
        return ResponseEntity.ok(participantAmountService.getParticipantAmountsByBillingRunId(id));
    }

    @GetMapping("/{id}/billingDocuments")
    public ResponseEntity<List<BillingDocumentDTO>> getBillingDocumentsByBillingRunId(
            @PathVariable(name = "id") final UUID id) {
        BillingRunDTO billingRunDTO = billingRunService.get(id);
        TenantContext.validateTenant(billingRunDTO.getTenantId());
        return ResponseEntity.ok(billingDocumentService.findByBillingRunId(id));
    }

    @GetMapping("/{id}/billingDocuments/xlsx")
    public ResponseEntity<ByteArrayResource> exportBillingDocumentsXlsx(
            @PathVariable(name = "id") final UUID id) throws IOException {
        BillingRunDTO billingRunDTO = billingRunService.get(id);
        TenantContext.validateTenant(billingRunDTO.getTenantId());

        Object lock = inMemoryLockRepository.getLock(billingRunDTO.getTenantId());
        synchronized (lock) {
            try {
                ByteArrayResource resource = new ByteArrayResource(
                        billingDocumentXlsxService.createXlsx(billingRunDTO.getId()));

                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("application/octet-stream"))
                        .contentLength(resource.contentLength())
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment()
                                        .filename("abrechnung_"+billingRunDTO.getClearingPeriodIdentifier()+"_export.xlsx")
                                        .build().toString())
                        .body(resource);
            } finally {
                inMemoryLockRepository.releaseLock(billingRunDTO.getTenantId());
            }
        }

    }

    @GetMapping("/{id}/billingDocuments/archive")
    public ResponseEntity<ByteArrayResource> exportBillingDocumentsArchive(
            @PathVariable(name = "id") final UUID id) throws IOException {

        BillingRunDTO billingRunDTO = billingRunService.get(id);
        TenantContext.validateTenant(billingRunDTO.getTenantId());

        Object lock = inMemoryLockRepository.getLock(billingRunDTO.getTenantId());
        synchronized (lock) {
            try {
                List<BillingDocumentDTO> billingDocumentDTOs = billingDocumentService.findByBillingRunId(id);

                ByteArrayResource resource = new ByteArrayResource(
                        billingDocumentArchiveService.createArchive(id));

                return ResponseEntity.ok()
                        .contentType(MediaType.valueOf("application/zip"))
                        .contentLength(resource.contentLength())
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment()
                                        .filename("abrechnung_"+billingRunDTO.getClearingPeriodIdentifier()+"_export.zip")
                                        .build().toString())
                        .body(resource);
            } finally {
                inMemoryLockRepository.releaseLock(billingRunDTO.getTenantId());
            }
        }
    }

    @GetMapping("/{id}/billingDocuments/sendmail")
    public ResponseEntity<String> sendAllBillingDocuments(
            @PathVariable(name = "id") final UUID id) {
        BillingRunDTO billingRunDTO = billingRunService.get(id);
        TenantContext.validateTenant(billingRunDTO.getTenantId());

        Object lock = inMemoryLockRepository.getLock(billingRunDTO.getTenantId());
        synchronized (lock) {
            try {
                try {
                    String sendProtocol = billingDocumentMailService.sendAllBillingDocuments(id);
                    return new ResponseEntity<>(sendProtocol, HttpStatus.OK);
                } catch (Exception e) {
                    return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                inMemoryLockRepository.releaseLock(billingRunDTO.getTenantId());
            }
        }

    }

//    @PostMapping
//    @ApiResponse(responseCode = "201")
//    public ResponseEntity<UUID> createBillingRun(
//            @RequestBody @Valid final BillingRunDTO billingRunDTO) {
//        final UUID createdId = billingRunService.create(billingRunDTO);
//        return new ResponseEntity<>(createdId, HttpStatus.CREATED);
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<Void> updateBillingRun(@PathVariable(name = "id") final UUID id,
//            @RequestBody @Valid final BillingRunDTO billingRunDTO) {
//        billingRunService.update(id, billingRunDTO);
//        return ResponseEntity.ok().build();
//    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteBillingRun(@PathVariable(name = "id") final UUID id) {
        BillingRunDTO billingRunDTO = billingRunService.get(id);
        TenantContext.validateTenant(billingRunDTO.getTenantId());

        Object lock = inMemoryLockRepository.getLock(billingRunDTO.getTenantId());
        synchronized (lock) {
            try {
                billingRunService.delete(id);
                return ResponseEntity.noContent().build();
            } finally {
                inMemoryLockRepository.releaseLock(billingRunDTO.getTenantId());
            }
        }

    }

}
