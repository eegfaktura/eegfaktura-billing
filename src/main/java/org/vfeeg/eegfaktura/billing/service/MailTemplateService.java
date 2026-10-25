package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.MessagingException;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.MailFile;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.MailTemplate;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.MailTemplateSummary;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.Preview;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.PreviewRequest;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.SaveRequest;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.TestRequest;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.TestResult;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.ValidationError;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.util.AppProperties;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * EEG-editable mail texts of billing (konzept-mail-texte-editierbar.md): storage, validation,
 * preview, test mail, and the text used when a billing run is sent. Files, validation and the
 * placeholder values are in {@link MailTemplateFiles}, {@link MailTemplateValidation} and
 * {@link MailTemplateValues}.
 *
 * Tenant isolation: the resource passes the request tenant (checked against the token by
 * JwtRequestFilter); every stored row is loaded first and then checked with
 * {@link TenantContext#validateTenant}.
 */
@Service
@Slf4j
public class MailTemplateService {

    /** Content-ID of the built-in logo, as in eegfaktura-backend. */
    public static final String LOGO_CONTENT_ID = "eegfaktura-logo-1";
    public static final String RECIPIENT_ME = "me";
    public static final String RECIPIENT_EEG = "eeg";

    /** 400 with the field errors of section H. */
    public static class ValidationException extends RuntimeException {
        private final List<ValidationError> errors;

        public ValidationException(List<ValidationError> errors) {
            super(errors.stream().map(ValidationError::message).collect(Collectors.joining("; ")));
            this.errors = List.copyOf(errors);
        }

        public ValidationException(String field, String message) {
            this(List.of(new ValidationError(field, message)));
        }

        public List<ValidationError> getErrors() {
            return errors;
        }
    }

    /** 502: the test mail could not be handed to the mail server. */
    public static class TestMailFailedException extends RuntimeException {
        public TestMailFailedException(Throwable cause) {
            super("Die Testmail konnte nicht verschickt werden.", cause);
        }
    }

    /** 409: the stored version is not the one the client edited. */
    public static class ConflictException extends RuntimeException {
        private final int currentVersion;

        public ConflictException(int currentVersion) {
            this("Die E-Mail-Vorlage wurde inzwischen geändert.", currentVersion);
        }

        public ConflictException(String message, int currentVersion) {
            super(message);
            this.currentVersion = currentVersion;
        }

        public int getCurrentVersion() {
            return currentVersion;
        }
    }

    /** A file's bytes and its checked MIME type. */
    public record FileContent(String mimeType, byte[] data) {
    }

    /** An image of a mail text, loaded for rendering. */
    public record StoredImage(String mimeType, byte[] data) {
    }

    /**
     * The text a send run uses, loaded once at its start (edge case "mail changed during a run").
     * {@code failure} is set when the stored text could not be used and the default is sent.
     */
    public record Prepared(boolean customized, MailTemplateCatalog.Entry entry, String subject, String bodyHtml,
                           Map<String, StoredImage> images, String failure) {
    }

    /** A rendered mail: subject, complete HTML and the images to embed (cid). */
    public record RenderedMail(String subject, String html, List<EmailService.InlineImage> inlineImages) {
    }

    record Validated(String subject, String bodyHtml, Set<String> fileIds) {
    }

    private final MailTemplateCatalog catalog;
    private final MailHtmlPolicy mailHtmlPolicy;
    private final MailTemplateRenderer renderer;
    private final BillingMailTemplateRepository templateRepository;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final MailTemplateFiles files;
    private final MailTemplateValidation validation;
    private final MailTemplateValues values;
    private final MailTemplateSending sending;

    public MailTemplateService(MailTemplateCatalog catalog, MailHtmlPolicy mailHtmlPolicy,
                               MailTemplateRenderer renderer, BillingMailTemplateRepository templateRepository,
                               BillingMailTemplateFileRepository fileRepository,
                               FileDataRepository fileDataRepository,
                               BillingMasterdataRepository billingMasterdataRepository,
                               BillingConfigRepository billingConfigRepository, EmailService emailService,
                               AppProperties appProperties, EntityManager entityManager) {
        this.catalog = catalog;
        this.mailHtmlPolicy = mailHtmlPolicy;
        this.renderer = renderer;
        this.templateRepository = templateRepository;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.files = new MailTemplateFiles(fileRepository, fileDataRepository, entityManager);
        this.validation = new MailTemplateValidation(mailHtmlPolicy, renderer);
        this.values = new MailTemplateValues(billingMasterdataRepository, billingConfigRepository);
        this.sending = new MailTemplateSending(catalog, mailHtmlPolicy, renderer, templateRepository, files);
    }

    static String normalizeTenant(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("No tenant");
        }
        return tenant.strip().toUpperCase(Locale.ROOT);
    }

    private MailTemplateCatalog.Entry entry(String key) {
        return catalog.find(key).orElseThrow(NotFoundException::new);
    }

    private Optional<BillingMailTemplate> loadTemplate(String tenant, String key) {
        Optional<BillingMailTemplate> template = templateRepository.findByTenantIdAndMailKey(tenant, key);
        template.ifPresent(t -> TenantContext.validateTenant(t.getTenantId()));
        return template;
    }

    // ---------------------------------------------------------------- API

    public List<MailTemplateSummary> list(String requestTenant) {
        String tenant = normalizeTenant(requestTenant);
        Map<String, BillingMailTemplate> stored = new HashMap<>();
        for (BillingMailTemplate template : templateRepository.findByTenantId(tenant)) {
            TenantContext.validateTenant(template.getTenantId());
            stored.put(template.getMailKey(), template);
        }
        return catalog.all().stream()
                .sorted(Comparator.comparing(MailTemplateCatalog.Entry::key))
                .map(entry -> {
                    BillingMailTemplate template = stored.get(entry.key());
                    return new MailTemplateSummary(entry.key(), entry.name(), entry.description(),
                            template != null,
                            template == null ? null : template.getLastUpdated(),
                            template == null ? null : template.getUpdatedBy(),
                            template != null && template.getDefaultVersion() < entry.defaultVersion());
                })
                .toList();
    }

    public MailTemplate get(String requestTenant, String key) {
        String tenant = normalizeTenant(requestTenant);
        MailTemplateCatalog.Entry entry = entry(key);
        return toDto(entry, loadTemplate(tenant, key).orElse(null), files.loadFiles(tenant, key));
    }

    @Transactional
    public MailTemplate save(String requestTenant, String key, SaveRequest request, String user) {
        String tenant = normalizeTenant(requestTenant);
        MailTemplateCatalog.Entry entry = entry(key);
        if (request == null) {
            throw new ValidationException("bodyHtml", "Kein Inhalt übergeben.");
        }
        files.lockMail(tenant, key);
        // every well-formed image id stays in the text; whether the file exists (also as an older
        // draft) is checked here under the lock, so a vanished image is a 400, never dropped silently
        Validated validated = validate(entry, request.subject(), request.bodyHtml(), id -> true);
        if (request.version() == null || request.version() < 0) {
            throw new ValidationException("version", "Version fehlt.");
        }
        noAttachment(entry, request.attachmentFileId());
        Set<String> existingImages = MailTemplateFiles.imageIds(files.loadAllFiles(tenant, key));
        for (String id : validated.fileIds()) {
            if (!MailHtmlPolicy.BUILTIN_LOGO_ID.equals(id) && !existingImages.contains(id)) {
                throw new ValidationException("bodyHtml", "Ein Bild im Text gibt es nicht (mehr); bitte erneut einfügen.");
            }
        }

        Optional<BillingMailTemplate> existing = templateRepository.lockByTenantIdAndMailKey(tenant, key);
        existing.ifPresent(t -> TenantContext.validateTenant(t.getTenantId()));
        int currentVersion = existing.map(BillingMailTemplate::getVersion).orElse(0);
        if (request.version() != currentVersion) {
            throw new ConflictException(currentVersion);
        }

        BillingMailTemplate template = existing.orElseGet(() -> {
            BillingMailTemplate created = new BillingMailTemplate();
            created.setTenantId(tenant);
            created.setMailKey(key);
            return created;
        });
        template.setSubject(validated.subject());
        template.setBodyHtml(validated.bodyHtml());
        template.setVersion(currentVersion + 1);
        template.setDefaultVersion(entry.defaultVersion());
        template.setUpdatedBy(user);
        try {
            template = templateRepository.saveAndFlush(template);
        } catch (DataIntegrityViolationException e) {
            // a concurrent first save of the same mail won (unique tenant_id + mail_key)
            throw new ConflictException(1);
        }

        // section H: the referenced files become part of the version; files of the previous version
        // it no longer uses and drafts older than 24 h are deleted; fresh drafts (other editors) stay
        List<BillingMailTemplateFile> kept = new ArrayList<>();
        for (BillingMailTemplateFile file : files.loadAllFiles(tenant, key)) {
            boolean used = validated.fileIds().contains(file.getId().toString());
            if (used) {
                files.commit(file);
                kept.add(file);
            } else if (file.isCommitted() || MailTemplateFiles.stale(file)) {
                files.deleteFile(file);
            } else {
                kept.add(file);
            }
        }
        return toDto(entry, template, kept);
    }

    @Transactional
    public void reset(String requestTenant, String key) {
        String tenant = normalizeTenant(requestTenant);
        entry(key);
        files.lockMail(tenant, key);
        loadTemplate(tenant, key).ifPresent(templateRepository::delete);
        files.loadAllFiles(tenant, key).forEach(files::deleteFile);
    }

    @Transactional
    public MailFile upload(String requestTenant, String key, String kind, MultipartFile multipartFile) {
        String tenant = normalizeTenant(requestTenant);
        MailTemplateCatalog.Limits limits = entry(key).limits();
        return files.upload(tenant, key, limits, kind, multipartFile);
    }

    public FileContent file(String requestTenant, String key, String fileId) {
        String tenant = normalizeTenant(requestTenant);
        entry(key);
        return files.file(tenant, key, fileId);
    }

    @Transactional
    public void deleteFile(String requestTenant, String key, String fileId) {
        String tenant = normalizeTenant(requestTenant);
        entry(key);
        if (MailHtmlPolicy.BUILTIN_LOGO_ID.equals(fileId)) {
            throw new NotFoundException(); // built in, not deletable
        }
        UUID id = MailTemplateFiles.parseFileId(fileId);
        files.lockMail(tenant, key);
        BillingMailTemplateFile file = files.loadFile(tenant, key, id);
        if (file.isCommitted()) {
            Optional<BillingMailTemplate> stored = loadTemplate(tenant, key);
            if (stored.isPresent() && mailHtmlPolicy.inspect(stored.get().getBodyHtml()).fileIds()
                    .contains(file.getId().toString())) {
                // section H: save the text without it first
                throw new ConflictException("Die Datei wird von der gespeicherten Fassung verwendet.",
                        stored.get().getVersion());
            }
        }
        files.deleteFile(file);
    }

    public Preview preview(String requestTenant, String key, PreviewRequest request) {
        String tenant = normalizeTenant(requestTenant);
        MailTemplateCatalog.Entry entry = entry(key);
        noAttachment(entry, request == null ? null : request.attachmentFileId());
        Prepared prepared = prepareUnsaved(tenant, entry,
                request == null ? null : request.subject(), request == null ? null : request.bodyHtml());
        RenderedMail mail = render(prepared, sampleValues(tenant), false);
        return new Preview(mail.subject(), mail.html());
    }

    /** billing-document has no extra PDF: attachmentFileId (section H) may only be absent/null. */
    private static void noAttachment(MailTemplateCatalog.Entry entry, String attachmentFileId) {
        if (attachmentFileId != null && entry.limits().maxAttachments() == 0) {
            throw new ValidationException("attachmentFileId", "Für diese E-Mail ist kein Anhang möglich.");
        }
    }

    public TestResult sendTest(String requestTenant, String key, TestRequest request, String loginEmail) {
        String tenant = normalizeTenant(requestTenant);
        MailTemplateCatalog.Entry entry = entry(key);
        if (request == null) {
            throw new ValidationException("recipient", "Empfänger fehlt.");
        }
        noAttachment(entry, request.attachmentFileId());
        Prepared prepared = prepareUnsaved(tenant, entry, request.subject(), request.bodyHtml());
        String to = values.testRecipient(tenant, request.recipient(), loginEmail);
        RenderedMail mail = render(prepared, sampleValues(tenant), true);
        try {
            // no cc to the EEG, no invoice PDF: only the text with sample member data
            emailService.sendEmail(appProperties.getNoReplyTo(), to, null, "[Test] " + mail.subject(),
                    mail.html(), mail.inlineImages(), Map.of());
        } catch (MessagingException | org.springframework.mail.MailException e) {
            log.warn("Test mail for {} not sent: {}", tenant, e.getMessage());
            throw new TestMailFailedException(e);
        }
        return new TestResult(to);
    }

    private Prepared prepareUnsaved(String tenant, MailTemplateCatalog.Entry entry, String subject, String bodyHtml) {
        List<BillingMailTemplateFile> mailFiles = files.loadFiles(tenant, entry.key());
        Validated validated = validate(entry, subject, bodyHtml, MailTemplateFiles.imageIds(mailFiles));
        return new Prepared(true, entry, validated.subject(), validated.bodyHtml(),
                files.loadImages(mailFiles, validated.fileIds()), null);
    }

    // package-private for the shared case test (mail-html-policy-cases.json)
    Validated validate(MailTemplateCatalog.Entry entry, String subject, String bodyHtml, Set<String> imageIds) {
        return validate(entry, subject, bodyHtml, (Predicate<String>) imageIds::contains);
    }

    Validated validate(MailTemplateCatalog.Entry entry, String subject, String bodyHtml, Predicate<String> imageIds) {
        return validation.validate(entry, subject, bodyHtml, imageIds);
    }

    private MailTemplate toDto(MailTemplateCatalog.Entry entry, BillingMailTemplate template,
                               List<BillingMailTemplateFile> templateFiles) {
        // name and type only, without the bytes (those come per file via GET .../files/{id})
        List<MailFile> mailFiles = files.mailFiles(templateFiles);
        boolean customized = template != null;
        return new MailTemplate(entry.key(), entry.name(), entry.description(),
                customized ? template.getSubject() : entry.defaultSubject(),
                customized ? template.getBodyHtml() : entry.defaultBodyHtml(),
                customized,
                customized ? template.getVersion() : 0,
                entry.defaultVersion(),
                customized && template.getDefaultVersion() < entry.defaultVersion(),
                entry.defaultSubject(), entry.defaultBodyHtml(),
                customized ? template.getLastUpdated() : null,
                customized ? template.getUpdatedBy() : null,
                // billing-document has no extra PDF and no hand-made volume template
                entry.placeholders(), mailFiles, entry.limits(), null, false);
    }

    // ---------------------------------------------------------------- sending

    /**
     * The text for the billing-document mails of a send run: the tenant's stored text, else the
     * default. A stored text that cannot be rendered (e.g. a placeholder that no longer exists)
     * yields the default with {@code failure} set. Never throws for a stored text.
     */
    public Prepared prepareForSend(String runTenant) {
        return sending.prepareForSend(runTenant);
    }

    /** The default text, rendered (fallback for a single mail). */
    public RenderedMail renderDefault(Map<String, String> values) {
        return render(sending.defaultPrepared(null), values, true);
    }

    /**
     * Renders a prepared text. Images become {@code cid:} references with the bytes in
     * {@link RenderedMail#inlineImages()} ({@code inline}) or {@code data:} URIs (preview).
     */
    public RenderedMail render(Prepared prepared, Map<String, String> values, boolean inline) {
        return sending.render(prepared, values, inline);
    }

    /** The placeholder values of a billing document (issuer data of the document). */
    public static Map<String, String> values(BillingDocument document) {
        return MailTemplateValues.values(document);
    }

    /**
     * Data for preview and test mail: a sample member and document, the real EEG data of the
     * tenant (billing masterdata, footer text of the billing config) where present.
     */
    Map<String, String> sampleValues(String tenant) {
        return values.sampleValues(tenant);
    }
}
