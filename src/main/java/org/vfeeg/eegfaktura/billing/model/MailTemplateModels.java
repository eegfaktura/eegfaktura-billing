package org.vfeeg.eegfaktura.billing.model;

import org.vfeeg.eegfaktura.billing.service.MailTemplateCatalog;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request and response bodies of /api/mailtemplates (konzept-mail-texte-editierbar.md, section H;
 * same schema as eegfaktura-backend).
 */
public final class MailTemplateModels {

    private MailTemplateModels() {
    }

    public record MailTemplateSummary(String key, String name, String description, boolean customized,
                                      OffsetDateTime updatedAt, String updatedBy, boolean defaultChanged) {
    }

    public record MailTemplate(String key, String name, String description, String subject, String bodyHtml,
                               boolean customized, int version, int defaultVersion, boolean defaultChanged,
                               String defaultSubject, String defaultBodyHtml, OffsetDateTime updatedAt,
                               String updatedBy, List<MailTemplateCatalog.Placeholder> placeholders,
                               List<MailFile> files, MailTemplateCatalog.Limits limits, String attachmentFileId,
                               boolean legacyTemplateActive) {
    }

    public record MailFile(String id, String kind, String filename, String mimeType, long size, boolean committed) {
    }

    public record SaveRequest(String subject, String bodyHtml, Integer version, String attachmentFileId) {
    }

    public record PreviewRequest(String subject, String bodyHtml, String attachmentFileId) {
    }

    public record Preview(String subject, String html) {
    }

    public record TestRequest(String recipient, String subject, String bodyHtml, String attachmentFileId) {
    }

    public record TestResult(String sentTo) {
    }

    public record ValidationError(String field, String message) {
    }

    public record ValidationErrors(List<ValidationError> errors) {
    }

    public record Conflict(String message, int currentVersion) {
    }
}
