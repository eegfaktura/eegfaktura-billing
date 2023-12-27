package org.vfeeg.eegfaktura.billing.rest;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vfeeg.eegfaktura.billing.model.FileDataDTO;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.FileDataService;


@RestController
@RequestMapping(value = "/api/fileData", produces = MediaType.APPLICATION_JSON_VALUE)
public class FileDataResource {

    private final FileDataService fileDataService;

    public FileDataResource(final FileDataService fileDataService) {
        this.fileDataService = fileDataService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ByteArrayResource> getFileData(@PathVariable(name = "id") final UUID id) throws IOException {
        FileDataDTO fileDataDTO = fileDataService.get(id);
        TenantContext.validateTenant(fileDataDTO.getTenantId());
        ByteArrayResource resource = new ByteArrayResource(fileDataDTO.getData());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(fileDataDTO.getMimeType()))
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileDataDTO.getName())
                                .build().toString())
                .body(resource);
    }



}
