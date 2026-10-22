package org.vfeeg.eegfaktura.billing.service;

import jakarta.mail.MessagingException;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;
import org.vfeeg.eegfaktura.billing.domain.BillingMasterdata;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.domain.FileData;
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
import org.vfeeg.eegfaktura.billing.util.ClearingPeriodIdentifierTool;
import org.vfeeg.eegfaktura.billing.util.EmailAddressUtil;
import org.vfeeg.eegfaktura.billing.util.FileTypeDetector;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * EEG-editable mail texts of billing (konzept-mail-texte-editierbar.md): storage, validation,
 * preview, test mail, and the text used when a billing run is sent.
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
    private final BillingMailTemplateFileRepository fileRepository;
    private final FileDataRepository fileDataRepository;
    private final BillingMasterdataRepository billingMasterdataRepository;
    private final BillingConfigRepository billingConfigRepository;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final EntityManager entityManager;
    private final byte[] defaultLogo;

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
        this.fileRepository = fileRepository;
        this.fileDataRepository = fileDataRepository;
        this.billingMasterdataRepository = billingMasterdataRepository;
        this.billingConfigRepository = billingConfigRepository;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.entityManager = entityManager;
        try {
            this.defaultLogo = new ClassPathResource("eeg-faktura-logo.png").getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("eeg-faktura-logo.png missing", e);
        }
    }

    static String normalizeTenant(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("No tenant");
        }
        return tenant.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Serializes uploads, saves and deletes of one mail (tenant + key) until the end of the
     * transaction, so the file limits hold for parallel uploads (like the backend).
     */
    private void lockMail(String tenant, String key) {
        entityManager.createNativeQuery("select 1 from (select pg_advisory_xact_lock(hashtext(?1))) l")
                .setParameter(1, "billing_mail_template/" + tenant + "/" + key)
                .getSingleResult();
    }

    private MailTemplateCatalog.Entry entry(String key) {
        return catalog.find(key).orElseThrow(NotFoundException::new);
    }

    private Optional<BillingMailTemplate> loadTemplate(String tenant, String key) {
        Optional<BillingMailTemplate> template = templateRepository.findByTenantIdAndMailKey(tenant, key);
        template.ifPresent(t -> TenantContext.validateTenant(t.getTenantId()));
        return template;
    }

    /** Drafts older than this are not listed, not counted and removed by the next save. */
    static final Duration DRAFT_MAX_AGE = Duration.ofHours(24);

    private static boolean stale(BillingMailTemplateFile file) {
        return !file.isCommitted() && file.getDateCreated() != null
                && file.getDateCreated().isBefore(OffsetDateTime.now().minus(DRAFT_MAX_AGE));
    }

    /** All files of the mail, stale drafts included. */
    private List<BillingMailTemplateFile> loadAllFiles(String tenant, String key) {
        List<BillingMailTemplateFile> files = fileRepository.findByTenantIdAndMailKeyOrderByDateCreated(tenant, key);
        files.forEach(f -> TenantContext.validateTenant(f.getTenantId()));
        return files;
    }

    /** The files of the saved version and the drafts of the last 24 h. */
    private List<BillingMailTemplateFile> loadFiles(String tenant, String key) {
        return loadAllFiles(tenant, key).stream().filter(f -> !stale(f)).toList();
    }

    private static Set<String> imageIds(List<BillingMailTemplateFile> files) {
        return files.stream()
                .filter(f -> BillingMailTemplateFile.KIND_IMAGE.equals(f.getKind()))
                .map(f -> f.getId().toString())
                .collect(Collectors.toSet());
    }

    /** The built-in logo, usable by every tenant; not stored, not counted against the limits. */
    private StoredImage builtinLogo() {
        return new StoredImage(FileTypeDetector.PNG, defaultLogo);
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
        return toDto(entry, loadTemplate(tenant, key).orElse(null), loadFiles(tenant, key));
    }

    @Transactional
    public MailTemplate save(String requestTenant, String key, SaveRequest request, String user) {
        String tenant = normalizeTenant(requestTenant);
        MailTemplateCatalog.Entry entry = entry(key);
        if (request == null) {
            throw new ValidationException("bodyHtml", "Kein Inhalt übergeben.");
        }
        lockMail(tenant, key);
        List<BillingMailTemplateFile> files = loadFiles(tenant, key);
        Validated validated = validate(entry, request.subject(), request.bodyHtml(), imageIds(files));
        if (request.version() == null || request.version() < 0) {
            throw new ValidationException("version", "Version fehlt.");
        }
        noAttachment(entry, request.attachmentFileId());

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
        for (BillingMailTemplateFile file : loadAllFiles(tenant, key)) {
            boolean used = validated.fileIds().contains(file.getId().toString());
            if (used) {
                if (!file.isCommitted()) {
                    file.setCommitted(true);
                    fileRepository.save(file);
                }
                kept.add(file);
            } else if (file.isCommitted() || stale(file)) {
                deleteFile(file);
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
        lockMail(tenant, key);
        loadTemplate(tenant, key).ifPresent(templateRepository::delete);
        loadAllFiles(tenant, key).forEach(this::deleteFile);
    }

    @Transactional
    public MailFile upload(String requestTenant, String key, String kind, MultipartFile multipartFile) {
        String tenant = normalizeTenant(requestTenant);
        MailTemplateCatalog.Limits limits = entry(key).limits();
        boolean image = BillingMailTemplateFile.KIND_IMAGE.equals(kind);
        if (!image && !BillingMailTemplateFile.KIND_ATTACHMENT.equals(kind)) {
            throw new ValidationException("kind", "Art der Datei muss image oder attachment sein.");
        }
        if (!image && limits.maxAttachments() == 0) {
            throw new ValidationException("kind", "Für diese E-Mail ist kein Anhang möglich.");
        }
        byte[] data;
        try {
            data = multipartFile == null ? null : multipartFile.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (data == null || data.length == 0) {
            throw new ValidationException("file", "Keine Datei übergeben.");
        }
        // the type from the content; Content-Type and file name come from the client
        String mimeType = FileTypeDetector.detect(data)
                .filter(type -> image
                        ? type.equals(FileTypeDetector.PNG) || type.equals(FileTypeDetector.JPEG)
                        : type.equals(FileTypeDetector.PDF))
                .orElseThrow(() -> new ValidationException("file", image
                        ? "Nur Bilder im Format PNG oder JPEG sind erlaubt."
                        : "Nur PDF-Dateien sind als Anhang erlaubt."));

        lockMail(tenant, key);
        List<BillingMailTemplateFile> files = loadFiles(tenant, key);
        long sameKind = files.stream().filter(f -> kind.equals(f.getKind())).count();
        long total = files.stream().mapToLong(BillingMailTemplateFile::getSize).sum();
        int maxCount = image ? limits.maxImages() : limits.maxAttachments();
        int maxBytes = image ? limits.maxImageBytes() : limits.maxAttachmentBytes();
        if (sameKind >= maxCount) {
            throw new ValidationException("file", image
                    ? "Höchstens " + maxCount + " Bilder je E-Mail."
                    : "Höchstens " + maxCount + " Anhang je E-Mail.");
        }
        if (data.length > maxBytes) {
            throw new ValidationException("file", "Die Datei ist größer als " + (maxBytes / 1024) + " KB.");
        }
        if (total + data.length > limits.maxTotalBytes()) {
            throw new ValidationException("file",
                    "Alle Dateien zusammen dürfen höchstens " + (limits.maxTotalBytes() / 1024) + " KB groß sein.");
        }

        FileData fileData = new FileData();
        fileData.setTenantId(tenant);
        fileData.setName(filename(multipartFile.getOriginalFilename(), mimeType));
        fileData.setMimeType(mimeType);
        fileData.setData(data);
        fileData = fileDataRepository.save(fileData);

        BillingMailTemplateFile file = new BillingMailTemplateFile();
        file.setTenantId(tenant);
        file.setMailKey(key);
        file.setFileDataId(fileData.getId());
        file.setKind(kind);
        file.setSize(data.length);
        file.setCommitted(false); // a draft until a saved version uses it; never sent
        file = fileRepository.save(file);
        return new MailFile(file.getId().toString(), kind, fileData.getName(), mimeType, data.length, false);
    }

    /** Only the last path segment, no control characters, at most 200 characters. */
    static String filename(String original, String mimeType) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        if (name.isEmpty()) {
            name = switch (mimeType) {
                case FileTypeDetector.PNG -> "bild.png";
                case FileTypeDetector.JPEG -> "bild.jpg";
                default -> "anhang.pdf";
            };
        }
        return name;
    }

    private BillingMailTemplateFile loadFile(String tenant, String key, UUID id) {
        BillingMailTemplateFile file = fileRepository.findById(id).orElseThrow(NotFoundException::new);
        TenantContext.validateTenant(file.getTenantId());
        if (!file.getTenantId().equals(tenant) || !file.getMailKey().equals(key)) {
            throw new NotFoundException();
        }
        return file;
    }

    public FileContent file(String requestTenant, String key, String fileId) {
        String tenant = normalizeTenant(requestTenant);
        entry(key);
        if (MailHtmlPolicy.BUILTIN_LOGO_ID.equals(fileId)) {
            return new FileContent(FileTypeDetector.PNG, defaultLogo);
        }
        BillingMailTemplateFile file = loadFile(tenant, key, parseFileId(fileId));
        FileData fileData = fileDataRepository.findById(file.getFileDataId()).orElseThrow(NotFoundException::new);
        TenantContext.validateTenant(fileData.getTenantId());
        return new FileContent(fileData.getMimeType(), fileData.getData());
    }

    @Transactional
    public void deleteFile(String requestTenant, String key, String fileId) {
        String tenant = normalizeTenant(requestTenant);
        entry(key);
        if (MailHtmlPolicy.BUILTIN_LOGO_ID.equals(fileId)) {
            throw new NotFoundException(); // built in, not deletable
        }
        UUID id = parseFileId(fileId);
        lockMail(tenant, key);
        BillingMailTemplateFile file = loadFile(tenant, key, id);
        if (file.isCommitted()) {
            Optional<BillingMailTemplate> stored = loadTemplate(tenant, key);
            if (stored.isPresent() && mailHtmlPolicy.inspect(stored.get().getBodyHtml()).fileIds()
                    .contains(file.getId().toString())) {
                // section H: save the text without it first
                throw new ConflictException("Die Datei wird von der gespeicherten Fassung verwendet.",
                        stored.get().getVersion());
            }
        }
        deleteFile(file);
    }

    private static UUID parseFileId(String fileId) {
        try {
            return UUID.fromString(fileId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("id", "Ungültige Datei-ID.");
        }
    }

    private void deleteFile(BillingMailTemplateFile file) {
        fileRepository.delete(file);
        fileRepository.flush();
        fileDataRepository.deleteWithoutLoading(file.getFileDataId());
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
        String to;
        if (RECIPIENT_ME.equals(request.recipient())) {
            to = singleAddress(loginEmail);
            if (to == null) {
                throw new ValidationException("recipient", "Für dein Login ist keine E-Mail-Adresse hinterlegt.");
            }
        } else if (RECIPIENT_EEG.equals(request.recipient())) {
            // the EEG address comes from base.billing_masterdata, as in the billing run; that view
            // has rows only for an EEG with participants, otherwise the address is not known here
            // a ';' list: every valid address, like the backend (sentTo = "a; b")
            List<String> addresses = EmailAddressUtil.normalize(billingMasterdataRepository
                            .findFirstByTenantIdIgnoreCase(tenant).map(BillingMasterdata::getEecEmail).orElse(null))
                    .stream().filter(EmailAddressUtil::isValid).toList();
            to = addresses.isEmpty() ? null : String.join("; ", addresses);
            if (to == null) {
                throw new ValidationException("recipient", "Für die Gemeinschaft ist keine E-Mail-Adresse bekannt.");
            }
        } else {
            throw new ValidationException("recipient", "Empfänger muss me oder eeg sein.");
        }
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

    /** The first valid address of a ';'-separated value, or null. */
    private static String singleAddress(String raw) {
        return EmailAddressUtil.normalize(raw).stream().filter(EmailAddressUtil::isValid).findFirst().orElse(null);
    }

    private Prepared prepareUnsaved(String tenant, MailTemplateCatalog.Entry entry, String subject, String bodyHtml) {
        List<BillingMailTemplateFile> files = loadFiles(tenant, entry.key());
        Validated validated = validate(entry, subject, bodyHtml, imageIds(files));
        return new Prepared(true, entry, validated.subject(), validated.bodyHtml(),
                loadImages(files, validated.fileIds()), null);
    }

    private Map<String, StoredImage> loadImages(List<BillingMailTemplateFile> files, Set<String> referenced) {
        Map<String, StoredImage> images = new LinkedHashMap<>();
        images.put(MailHtmlPolicy.BUILTIN_LOGO_ID, builtinLogo());
        for (BillingMailTemplateFile file : files) {
            String id = file.getId().toString();
            if (BillingMailTemplateFile.KIND_IMAGE.equals(file.getKind()) && referenced.contains(id)) {
                fileDataRepository.findById(file.getFileDataId())
                        .filter(fd -> fd.getData() != null)
                        .ifPresent(fd -> images.put(id, new StoredImage(fd.getMimeType(), fd.getData())));
            }
        }
        return images;
    }

    // package-private for the shared case test (mail-html-policy-cases.json)
    Validated validate(MailTemplateCatalog.Entry entry, String subject, String bodyHtml, Set<String> imageIds) {
        List<ValidationError> errors = new ArrayList<>();
        Set<String> known = entry.placeholderKeys();

        if (subject == null || subject.isBlank()) {
            errors.add(new ValidationError("subject", "Betreff fehlt."));
        } else {
            if (subject.length() > MailTemplateCatalog.MAX_SUBJECT_LENGTH) {
                errors.add(new ValidationError("subject",
                        "Betreff ist länger als " + MailTemplateCatalog.MAX_SUBJECT_LENGTH + " Zeichen."));
            }
            for (String key : MailTemplateRenderer.subjectPlaceholders(subject)) {
                if (!known.contains(key)) {
                    errors.add(new ValidationError("subject", "Unbekannter Platzhalter: " + key));
                } else if (entry.blockKeys().contains(key)) {
                    errors.add(new ValidationError("subject", "Platzhalter im Betreff nicht möglich: " + key));
                }
            }
        }

        String sanitized = null;
        Set<String> fileIds = Set.of();
        if (bodyHtml == null || bodyHtml.isBlank()) {
            errors.add(new ValidationError("bodyHtml", "Text fehlt."));
        } else if (bodyHtml.getBytes(StandardCharsets.UTF_8).length > MailTemplateCatalog.MAX_BODY_BYTES) {
            errors.add(new ValidationError("bodyHtml", "Text ist größer als 100 KB."));
        } else {
            sanitized = mailHtmlPolicy.sanitize(bodyHtml, imageIds);
            MailHtmlPolicy.Inspection inspection = mailHtmlPolicy.inspect(sanitized);
            for (String key : inspection.placeholderKeys()) {
                if (!known.contains(key)) {
                    errors.add(new ValidationError("bodyHtml", "Unbekannter Platzhalter: " + key));
                }
            }
            // section H: a block (div[data-ph]) only on the top level, not inside a paragraph or span
            for (MailHtmlPolicy.PlaceholderUse use : inspection.placeholderUses()) {
                if (entry.blockKeys().contains(use.key()) && !("div".equals(use.element()) && use.topLevel())) {
                    errors.add(new ValidationError("bodyHtml",
                            "Der Baustein " + use.key() + " ist nur als eigener Absatz möglich."));
                }
            }
            if (errors.isEmpty()) {
                sanitized = renderer.relabel(sanitized, entry.placeholders().stream()
                        .collect(Collectors.toMap(MailTemplateCatalog.Placeholder::key,
                                MailTemplateCatalog.Placeholder::label)));
            }
            if (inspection.isEmpty()) {
                errors.add(new ValidationError("bodyHtml", "Text ist nach der Bereinigung leer."));
            } else if (sanitized.getBytes(StandardCharsets.UTF_8).length > MailTemplateCatalog.MAX_BODY_BYTES) {
                errors.add(new ValidationError("bodyHtml", "Text ist größer als 100 KB."));
            }
            fileIds = inspection.fileIds();
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Validated(subject.strip(), sanitized, fileIds);
    }

    private MailTemplate toDto(MailTemplateCatalog.Entry entry, BillingMailTemplate template,
                               List<BillingMailTemplateFile> files) {
        // name and type only, without the bytes (those come per file via GET .../files/{id})
        Map<UUID, FileDataRepository.FileMeta> meta = files.isEmpty() ? Map.of()
                : fileDataRepository.findMetaByIdIn(files.stream().map(BillingMailTemplateFile::getFileDataId).toList())
                        .stream().collect(Collectors.toMap(FileDataRepository.FileMeta::getId, Function.identity()));
        List<MailFile> mailFiles = files.stream()
                .map(f -> {
                    FileDataRepository.FileMeta m = meta.get(f.getFileDataId());
                    return new MailFile(f.getId().toString(), f.getKind(), m == null ? null : m.getName(),
                            m == null ? null : m.getMimeType(), f.getSize(), f.isCommitted());
                })
                .toList();
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

    private Prepared defaultPrepared(String failure) {
        MailTemplateCatalog.Entry entry = entry(MailTemplateCatalog.BILLING_DOCUMENT);
        return new Prepared(false, entry, entry.defaultSubject(), entry.defaultBodyHtml(),
                Map.of(MailHtmlPolicy.BUILTIN_LOGO_ID, builtinLogo()), failure);
    }

    /**
     * The text for the billing-document mails of a send run: the tenant's stored text, else the
     * default. A stored text that cannot be rendered (e.g. a placeholder that no longer exists)
     * yields the default with {@code failure} set. Never throws for a stored text.
     */
    public Prepared prepareForSend(String runTenant) {
        String tenant = runTenant == null ? "" : runTenant.strip().toUpperCase(Locale.ROOT);
        Optional<BillingMailTemplate> stored;
        try {
            stored = templateRepository.findByTenantIdAndMailKey(tenant, MailTemplateCatalog.BILLING_DOCUMENT);
        } catch (RuntimeException e) {
            log.warn("Mail template of {} not loadable, sending the default: {}", tenant, e.getMessage());
            return defaultPrepared("Vorlage nicht lesbar");
        }
        if (stored.isEmpty()) {
            return defaultPrepared(null);
        }
        try {
            BillingMailTemplate template = stored.get();
            MailTemplateCatalog.Entry entry = entry(MailTemplateCatalog.BILLING_DOCUMENT);
            // only files of the saved version; drafts are never sent
            List<BillingMailTemplateFile> files =
                    fileRepository.findByTenantIdAndMailKeyOrderByDateCreated(tenant, MailTemplateCatalog.BILLING_DOCUMENT)
                            .stream().filter(BillingMailTemplateFile::isCommitted).toList();
            Map<String, StoredImage> images = loadImages(files, imageIds(files));
            // sanitized once per run (defence in depth for stored rows); per mail only a re-render
            String body = mailHtmlPolicy.sanitize(template.getBodyHtml(), images.keySet());
            Prepared prepared = new Prepared(true, entry, template.getSubject(), body, images, null);
            render(prepared, Map.of(), true); // dry run: fails here instead of per mail
            return prepared;
        } catch (RuntimeException e) {
            log.warn("Mail template of {} not usable, sending the default: {}", tenant, e.getMessage());
            return defaultPrepared(e.getMessage());
        }
    }

    /** The default text, rendered (fallback for a single mail). */
    public RenderedMail renderDefault(Map<String, String> values) {
        return render(defaultPrepared(null), values, true);
    }

    /**
     * Renders a prepared text. Images become {@code cid:} references with the bytes in
     * {@link RenderedMail#inlineImages()} ({@code inline}) or {@code data:} URIs (preview).
     */
    public RenderedMail render(Prepared prepared, Map<String, String> values, boolean inline) {
        MailTemplateCatalog.Entry entry = prepared.entry();
        List<EmailService.InlineImage> inlineImages = new ArrayList<>();
        Function<String, String> imageSrc = id -> {
            StoredImage image = prepared.images().get(id);
            if (image == null) {
                return null;
            }
            if (!inline) {
                return "data:" + image.mimeType() + ";base64," + Base64.getEncoder().encodeToString(image.data());
            }
            String contentId = MailHtmlPolicy.BUILTIN_LOGO_ID.equals(id) ? LOGO_CONTENT_ID : "mailfile-" + id;
            if (inlineImages.stream().noneMatch(i -> i.contentId().equals(contentId))) {
                inlineImages.add(new EmailService.InlineImage(contentId, image.data(), image.mimeType()));
            }
            return "cid:" + contentId;
        };
        String subject = renderer.renderSubject(prepared.subject(), values, entry.placeholderKeys(), entry.htmlKeys());
        String body = renderer.renderBody(prepared.bodyHtml(), values,
                entry.placeholderKeys(), entry.blockKeys(), entry.htmlKeys(), imageSrc);
        return new RenderedMail(subject, renderer.frame(body), inlineImages);
    }

    /** The placeholder values of a billing document (issuer data of the document). */
    public static Map<String, String> values(BillingDocument document) {
        Map<String, String> values = new HashMap<>();
        values.put("recipient.name", document.getRecipientName());
        values.put("document.type", document.getBillingDocumentType() == null ? null
                : BillingDocument.getDocumentTypeName(document.getBillingDocumentType()));
        values.put("document.period", ClearingPeriodIdentifierTool.asText(document.getClearingPeriodIdentifier()));
        values.put("eeg.name", document.getIssuerName());
        values.put("eeg.email", document.getIssuerMail());
        values.put("eeg.contactBlock", contactBlock(document.getIssuerAddressLine1(), document.getIssuerAddressLine2(),
                document.getIssuerAddressLine3(), document.getIssuerPhone(), document.getIssuerMail(),
                document.getIssuerWebsite()));
        values.put("billing.footerText", document.getFooterText());
        return values;
    }

    private static String contactBlock(String line1, String line2, String line3, String phone, String email,
                                       String website) {
        return Stream.of(line1, line2, line3,
                        blank(phone) ? null : "Telefon: " + phone.strip(),
                        blank(email) ? null : "E-Mail: " + email.strip(),
                        blank(website) ? null : "Web: " + website.strip())
                .filter(s -> !blank(s))
                .map(String::strip)
                .collect(Collectors.joining("\n"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Data for preview and test mail: a sample member and document, the real EEG data of the
     * tenant (billing masterdata, footer text of the billing config) where present.
     */
    Map<String, String> sampleValues(String tenant) {
        BillingDocument sample = new BillingDocument();
        sample.setRecipientName("Max Mustermann");
        sample.setBillingDocumentType(BillingDocumentType.INVOICE);
        sample.setClearingPeriodIdentifier("Abr_YQ-2026-3");
        Optional<BillingMasterdata> masterdata = billingMasterdataRepository.findFirstByTenantIdIgnoreCase(tenant);
        if (masterdata.isPresent()) {
            BillingMasterdata eeg = masterdata.get();
            sample.setIssuerName(eeg.getEecName());
            sample.setIssuerMail(eeg.getEecEmail());
            sample.setIssuerPhone(eeg.getEecPhone());
            sample.setIssuerAddressLine1(eeg.getEecStreet());
            sample.setIssuerAddressLine2(Stream.of(eeg.getEecZipCode(), eeg.getEecCity())
                    .filter(Objects::nonNull).collect(Collectors.joining(" ")));
        } else {
            sample.setIssuerName("Energiegemeinschaft Musterdorf");
            sample.setIssuerMail("office@musterdorf.example");
            sample.setIssuerAddressLine1("Hauptplatz 1");
            sample.setIssuerAddressLine2("1234 Musterdorf");
        }
        sample.setFooterText(billingConfigRepository.findFirstByTenantIdIgnoreCase(tenant)
                .map(BillingConfig::getFooterText)
                .map(text -> text.replace("##", "\n"))
                .orElse(null));
        return values(sample);
    }
}
