package org.vfeeg.eegfaktura.billing.service;

import org.vfeeg.eegfaktura.billing.model.MailTemplateModels.ValidationError;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.Validated;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Checks and sanitizes subject and text of a mail (section H): length, placeholders, blocks only on
 * the top level, link rules ({@link MailLinkRules}). A collaborator of {@link MailTemplateService}.
 */
class MailTemplateValidation {

    private final MailHtmlPolicy mailHtmlPolicy;
    private final MailTemplateRenderer renderer;
    private final MailLinkRules linkRules;

    MailTemplateValidation(MailHtmlPolicy mailHtmlPolicy, MailTemplateRenderer renderer) {
        this.mailHtmlPolicy = mailHtmlPolicy;
        this.renderer = renderer;
        this.linkRules = new MailLinkRules(mailHtmlPolicy);
    }

    Validated validate(MailTemplateCatalog.Entry entry, String subject, String bodyHtml, Predicate<String> imageIds) {
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
            // link rules (Variante A): rejected with a message, never removed silently
            for (MailLinkRules.Violation violation : linkRules.check(sanitized)) {
                errors.add(new ValidationError("bodyHtml", violation.message()));
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
}
