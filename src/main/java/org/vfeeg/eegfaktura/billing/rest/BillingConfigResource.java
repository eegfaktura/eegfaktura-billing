package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.model.BillingConfigImageType;
import org.vfeeg.eegfaktura.billing.model.FileDataDTO;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.FileDataService;


@RestController
@RequestMapping(value = "/api/billingConfigs", produces = MediaType.APPLICATION_JSON_VALUE)
public class BillingConfigResource {

    private final BillingConfigService billingConfigService;
    private final FileDataService fileDataService;

    public BillingConfigResource(final BillingConfigService billingConfigService,
                                 final FileDataService fileDataService) {
        this.billingConfigService = billingConfigService;
        this.fileDataService = fileDataService;
    }

//    @GetMapping
//    public ResponseEntity<List<BillingConfigDTO>> getAllBillingConfigs() {
//        return ResponseEntity.ok(billingConfigService.findAll());
//    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<BillingConfigDTO> getBillingConfigByTenantId(
            @PathVariable(name = "tenantId") final String tenantId) {
        TenantContext.validateTenant(tenantId);
        return ResponseEntity.ok(billingConfigService.getByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingConfigDTO> getBillingConfig(
            @PathVariable(name = "id") final UUID id) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        return ResponseEntity.ok(billingConfigService.get(id));
    }

    @PostMapping("/{id}/logoImage")
    public void uploadLogoImage(
            @PathVariable(name = "id") final UUID id,
            @RequestParam("file") MultipartFile multiPartFile) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        billingConfigService.storeImage(billingConfigDTO, BillingConfigImageType.LOGO_IMAGE, multiPartFile);
    }

    @PostMapping("/{id}/footerImage")
    public void uploadFooterImage(
            @PathVariable(name = "id") final UUID id,
            @RequestParam("file") MultipartFile multiPartFile) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        billingConfigService.storeImage(billingConfigDTO, BillingConfigImageType.FOOTER_IMAGE, multiPartFile);
    }

    @DeleteMapping("/{id}/logoImage")
    public void deleteLogoImage(
            @PathVariable(name = "id") final UUID id) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        billingConfigService.deleteImage(billingConfigDTO, BillingConfigImageType.LOGO_IMAGE);
    }

    @DeleteMapping("/{id}/footerImage")
    public void deleteFooterImage(
            @PathVariable(name = "id") final UUID id) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        billingConfigService.deleteImage(billingConfigDTO, BillingConfigImageType.FOOTER_IMAGE);
    }

    @GetMapping("/{id}/logoImage")
    public ResponseEntity<byte[]> getLogoImage(
            @PathVariable(name = "id") final UUID id) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        FileDataDTO fileDataDTO = fileDataService.get(billingConfigDTO.getHeaderImageFileDataId());

        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(fileDataDTO.getMimeType()))
                .body(fileDataDTO.getData());
    }


    @GetMapping("/{id}/footerImage")
    public ResponseEntity<byte[]> getFooterImage(
            @PathVariable(name = "id") final UUID id,
            @RequestParam("file") MultipartFile multiPartFile) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        FileDataDTO fileDataDTO = fileDataService.get(billingConfigDTO.getFooterImageFileDataId());

        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(fileDataDTO.getMimeType()))
                .body(fileDataDTO.getData());
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<UUID> createBillingConfig(
            @RequestBody @Valid final BillingConfigDTO billingConfigDTO) {
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        final UUID createdId = billingConfigService.create(billingConfigDTO);
        return new ResponseEntity<>(createdId, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateBillingConfig(@PathVariable(name = "id") final UUID id,
                                                    @RequestBody @Valid final BillingConfigDTO billingConfigDTO) {
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        billingConfigService.update(id, billingConfigDTO);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteBillingConfig(@PathVariable(name = "id") final UUID id) {
        BillingConfigDTO billingConfigDTO = billingConfigService.get(id);
        TenantContext.validateTenant(billingConfigDTO.getTenantId());
        billingConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
