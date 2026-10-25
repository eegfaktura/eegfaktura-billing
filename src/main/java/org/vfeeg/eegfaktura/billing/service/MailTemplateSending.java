package org.vfeeg.eegfaktura.billing.service;

import lombok.extern.slf4j.Slf4j;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplateFile;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateRepository;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.Prepared;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.RenderedMail;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.StoredImage;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Rendering of a prepared mail text and the text a send run uses. A collaborator of
 * {@link MailTemplateService}, which keeps the public entry points.
 */
@Slf4j
class MailTemplateSending {

    private final MailTemplateCatalog catalog;
    private final MailHtmlPolicy mailHtmlPolicy;
    private final MailTemplateRenderer renderer;
    private final BillingMailTemplateRepository templateRepository;
    private final MailTemplateFiles files;

    MailTemplateSending(MailTemplateCatalog catalog, MailHtmlPolicy mailHtmlPolicy, MailTemplateRenderer renderer,
                        BillingMailTemplateRepository templateRepository, MailTemplateFiles files) {
        this.catalog = catalog;
        this.mailHtmlPolicy = mailHtmlPolicy;
        this.renderer = renderer;
        this.templateRepository = templateRepository;
        this.files = files;
    }

    private MailTemplateCatalog.Entry entry(String key) {
        return catalog.find(key).orElseThrow(NotFoundException::new);
    }

    Prepared defaultPrepared(String failure) {
        MailTemplateCatalog.Entry entry = entry(MailTemplateCatalog.BILLING_DOCUMENT);
        return new Prepared(false, entry, entry.defaultSubject(), entry.defaultBodyHtml(),
                Map.of(MailHtmlPolicy.BUILTIN_LOGO_ID, files.builtinLogo()), failure);
    }

    /** See {@link MailTemplateService#prepareForSend}. */
    Prepared prepareForSend(String runTenant) {
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
            List<BillingMailTemplateFile> committed =
                    files.committedFiles(tenant, MailTemplateCatalog.BILLING_DOCUMENT);
            Map<String, StoredImage> images = files.loadImages(committed, MailTemplateFiles.imageIds(committed));
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

    /** See {@link MailTemplateService#render}. */
    RenderedMail render(Prepared prepared, Map<String, String> values, boolean inline) {
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
            String contentId = MailHtmlPolicy.BUILTIN_LOGO_ID.equals(id)
                    ? MailTemplateService.LOGO_CONTENT_ID : "mailfile-" + id;
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
}
