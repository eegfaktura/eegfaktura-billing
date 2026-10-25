package org.vfeeg.eegfaktura.billing.service;

import jakarta.persistence.EntityManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.multipart.MultipartFile;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;
import org.vfeeg.eegfaktura.billing.domain.FileData;
import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.MailFile;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateFileRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.security.TenantContext;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.FileContent;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.StoredImage;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.ValidationException;
import org.vfeeg.eegfaktura.billing.util.FileTypeDetector;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The files (images, attachments) of the mail texts: drafts, limits at upload, loading for
 * rendering, deleting. A collaborator of {@link MailTemplateService}, which does the tenant
 * normalization and the catalog lookup before calling it.
 */
class MailTemplateFiles {

    /** Drafts older than this are not listed, not counted and removed by the next save. */
    static final Duration DRAFT_MAX_AGE = Duration.ofHours(24);

    private final BillingMailTemplateFileRepository fileRepository;
    private final FileDataRepository fileDataRepository;
    private final EntityManager entityManager;
    private final byte[] defaultLogo;

    MailTemplateFiles(BillingMailTemplateFileRepository fileRepository, FileDataRepository fileDataRepository,
                      EntityManager entityManager) {
        this.fileRepository = fileRepository;
        this.fileDataRepository = fileDataRepository;
        this.entityManager = entityManager;
        try {
            this.defaultLogo = new ClassPathResource("eeg-faktura-logo.png").getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("eeg-faktura-logo.png missing", e);
        }
    }

    /**
     * Serializes uploads, saves and deletes of one mail (tenant + key) until the end of the
     * transaction, so the file limits hold for parallel uploads (like the backend).
     */
    void lockMail(String tenant, String key) {
        entityManager.createNativeQuery("select 1 from (select pg_advisory_xact_lock(hashtext(?1))) l")
                .setParameter(1, "billing_mail_template/" + tenant + "/" + key)
                .getSingleResult();
    }

    static boolean stale(BillingMailTemplateFile file) {
        return !file.isCommitted() && file.getDateCreated() != null
                && file.getDateCreated().isBefore(OffsetDateTime.now().minus(DRAFT_MAX_AGE));
    }

    /** All files of the mail, stale drafts included. */
    List<BillingMailTemplateFile> loadAllFiles(String tenant, String key) {
        List<BillingMailTemplateFile> files = fileRepository.findByTenantIdAndMailKeyOrderByDateCreated(tenant, key);
        files.forEach(f -> TenantContext.validateTenant(f.getTenantId()));
        return files;
    }

    /** The files of the saved version and the drafts of the last 24 h. */
    List<BillingMailTemplateFile> loadFiles(String tenant, String key) {
        return loadAllFiles(tenant, key).stream().filter(f -> !stale(f)).toList();
    }

    static Set<String> imageIds(List<BillingMailTemplateFile> files) {
        return files.stream()
                .filter(f -> BillingMailTemplateFile.KIND_IMAGE.equals(f.getKind()))
                .map(f -> f.getId().toString())
                .collect(Collectors.toSet());
    }

    /**
     * The image limits over the images a saved version uses. The upload counts only the files of
     * the last 24 h, but a save may also commit older drafts, so the limits are checked again here.
     */
    static void checkSavedLimits(MailTemplateCatalog.Limits limits, List<BillingMailTemplateFile> files,
                                 Set<String> referenced) {
        List<BillingMailTemplateFile> used = files.stream()
                .filter(f -> BillingMailTemplateFile.KIND_IMAGE.equals(f.getKind()))
                .filter(f -> referenced.contains(f.getId().toString()))
                .toList();
        if (used.size() > limits.maxImages()) {
            throw new ValidationException("bodyHtml", "Höchstens " + limits.maxImages() + " Bilder je E-Mail.");
        }
        if (used.stream().mapToLong(BillingMailTemplateFile::getSize).sum() > limits.maxTotalBytes()) {
            throw new ValidationException("bodyHtml",
                    "Alle Bilder zusammen dürfen höchstens " + (limits.maxTotalBytes() / 1024) + " KB groß sein.");
        }
    }

    /** The built-in logo, usable by every tenant; not stored, not counted against the limits. */
    StoredImage builtinLogo() {
        return new StoredImage(FileTypeDetector.PNG, defaultLogo);
    }

    MailFile upload(String tenant, String key, MailTemplateCatalog.Limits limits, String kind,
                    MultipartFile multipartFile) {
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

    BillingMailTemplateFile loadFile(String tenant, String key, UUID id) {
        BillingMailTemplateFile file = fileRepository.findById(id).orElseThrow(NotFoundException::new);
        TenantContext.validateTenant(file.getTenantId());
        if (!file.getTenantId().equals(tenant) || !file.getMailKey().equals(key)) {
            throw new NotFoundException();
        }
        return file;
    }

    FileContent file(String tenant, String key, String fileId) {
        if (MailHtmlPolicy.BUILTIN_LOGO_ID.equals(fileId)) {
            return new FileContent(FileTypeDetector.PNG, defaultLogo);
        }
        BillingMailTemplateFile file = loadFile(tenant, key, parseFileId(fileId));
        FileData fileData = fileDataRepository.findById(file.getFileDataId()).orElseThrow(NotFoundException::new);
        TenantContext.validateTenant(fileData.getTenantId());
        return new FileContent(fileData.getMimeType(), fileData.getData());
    }

    static UUID parseFileId(String fileId) {
        try {
            return UUID.fromString(fileId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("id", "Ungültige Datei-ID.");
        }
    }

    void deleteFile(BillingMailTemplateFile file) {
        fileRepository.delete(file);
        fileRepository.flush();
        fileDataRepository.deleteWithoutLoading(file.getFileDataId());
    }

    /** Marks a draft as part of the saved version. */
    void commit(BillingMailTemplateFile file) {
        if (!file.isCommitted()) {
            file.setCommitted(true);
            fileRepository.save(file);
        }
    }

    Map<String, StoredImage> loadImages(List<BillingMailTemplateFile> files, Set<String> referenced) {
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

    /** The files of a mail for the DTO: name and type only, without the bytes (those come per file). */
    List<MailFile> mailFiles(List<BillingMailTemplateFile> files) {
        Map<UUID, FileDataRepository.FileMeta> meta = files.isEmpty() ? Map.of()
                : fileDataRepository.findMetaByIdIn(files.stream().map(BillingMailTemplateFile::getFileDataId).toList())
                        .stream().collect(Collectors.toMap(FileDataRepository.FileMeta::getId, Function.identity()));
        return files.stream()
                .map(f -> {
                    FileDataRepository.FileMeta m = meta.get(f.getFileDataId());
                    return new MailFile(f.getId().toString(), f.getKind(), m == null ? null : m.getName(),
                            m == null ? null : m.getMimeType(), f.getSize(), f.isCommitted());
                })
                .toList();
    }

    /** Only the files of the saved version; drafts are never sent. */
    List<BillingMailTemplateFile> committedFiles(String tenant, String key) {
        return fileRepository.findByTenantIdAndMailKeyOrderByDateCreated(tenant, key)
                .stream().filter(BillingMailTemplateFile::isCommitted).toList();
    }
}
