package org.vfeeg.eegfaktura.billing.rest;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.model.ErrorResponse;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.Conflict;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.MailFile;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.MailTemplate;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.MailTemplateSummary;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.Preview;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.PreviewRequest;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.SaveRequest;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.TestRequest;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.TestResult;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.ValidationError;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.ValidationErrors;
import org.vfeeg.eegfaktura.billing.security.JwtAuthentication;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService;

import java.util.List;
import java.util.Map;

/**
 * EEG-editable mail texts of billing (konzept-mail-texte-editierbar.md, section H; same schema as
 * eegfaktura-backend under /eeg/mailtemplates). Role EEG_ADMIN is required globally
 * (JwtSecurityConfig); the tenant is the request's "Tenant" header, checked against the token by
 * JwtRequestFilter and against every loaded row in the service.
 */
@RestController
@RequestMapping(value = "/api/mailtemplates", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class MailTemplateResource {

    private final MailTemplateService mailTemplateService;

    public MailTemplateResource(final MailTemplateService mailTemplateService) {
        this.mailTemplateService = mailTemplateService;
    }

    private static String requestTenant() {
        var current = TenantContext.getCurrentTenant();
        String tenant = current == null ? null : current.getAuthority();
        if (tenant == null || tenant.isBlank()) {
            throw new AccessDeniedException("No tenant");
        }
        return tenant;
    }

    @GetMapping
    public List<MailTemplateSummary> list() {
        return mailTemplateService.list(requestTenant());
    }

    @GetMapping("/{key}")
    public MailTemplate get(@PathVariable(name = "key") final String key) {
        return mailTemplateService.get(requestTenant(), key);
    }

    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MailTemplate save(@PathVariable(name = "key") final String key,
                             @RequestBody final SaveRequest request,
                             final Authentication authentication) {
        return mailTemplateService.save(requestTenant(), key, request,
                authentication == null ? null : authentication.getName());
    }

    @DeleteMapping("/{key}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> reset(@PathVariable(name = "key") final String key) {
        mailTemplateService.reset(requestTenant(), key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{key}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "201")
    public ResponseEntity<MailFile> upload(@PathVariable(name = "key") final String key,
                                           @RequestParam(name = "file", required = false) final MultipartFile file,
                                           @RequestParam(name = "kind", required = false) final String kind) {
        return new ResponseEntity<>(mailTemplateService.upload(requestTenant(), key, kind, file), HttpStatus.CREATED);
    }

    @GetMapping(value = "/{key}/files/{id}", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> file(@PathVariable(name = "key") final String key,
                                       @PathVariable(name = "id") final String id) {
        MailTemplateService.FileContent content = mailTemplateService.file(requestTenant(), key, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(content.data());
    }

    @DeleteMapping("/{key}/files/{id}")
    @ApiResponse(responseCode = "204")
    public ResponseEntity<Void> deleteFile(@PathVariable(name = "key") final String key,
                                           @PathVariable(name = "id") final String id) {
        mailTemplateService.deleteFile(requestTenant(), key, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{key}/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Preview preview(@PathVariable(name = "key") final String key,
                           @RequestBody final PreviewRequest request) {
        return mailTemplateService.preview(requestTenant(), key, request);
    }

    @PostMapping(value = "/{key}/test", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TestResult test(@PathVariable(name = "key") final String key,
                           @RequestBody final TestRequest request,
                           final Authentication authentication) {
        // "me" is the e-mail claim of the token; no recipient can be passed in
        String loginEmail = authentication instanceof JwtAuthentication jwt ? jwt.getEmail() : null;
        return mailTemplateService.sendTest(requestTenant(), key, request, loginEmail);
    }

    @ExceptionHandler(MailTemplateService.ValidationException.class)
    public ResponseEntity<ValidationErrors> handleValidation(final MailTemplateService.ValidationException e) {
        return ResponseEntity.badRequest().body(new ValidationErrors(e.getErrors()));
    }

    /** Unreadable JSON or a malformed file id: 400 instead of the global 500. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ValidationErrors> handleBadRequest(final Exception e) {
        String field = e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "body";
        return ResponseEntity.badRequest().body(new ValidationErrors(
                List.of(new ValidationError(field, "Ungültige Anfrage."))));
    }

    @ExceptionHandler(MailTemplateService.TestMailFailedException.class)
    public ResponseEntity<Map<String, String>> handleTestMailFailed(final MailTemplateService.TestMailFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MailTemplateService.ConflictException.class)
    public ResponseEntity<Conflict> handleConflict(final MailTemplateService.ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Conflict(e.getMessage(), e.getCurrentVersion()));
    }

    /** Wrong tenant: 403 with a generic message (the global handler would answer 500). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(final AccessDeniedException e) {
        final ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setHttpStatus(HttpStatus.FORBIDDEN.value());
        errorResponse.setException(AccessDeniedException.class.getSimpleName());
        errorResponse.setMessage("Access denied");
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
}
