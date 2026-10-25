package org.vfeeg.eegfaktura.billing.service;

import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.BillingDocument;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.domain.BillingMasterdata;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService.ValidationException;
import org.vfeeg.eegfaktura.billing.util.ClearingPeriodIdentifierTool;
import org.vfeeg.eegfaktura.billing.util.EmailAddressUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The placeholder values of a mail (from a billing document, or sample data for preview and test
 * mail) and the address of a test mail. A collaborator of {@link MailTemplateService}.
 */
class MailTemplateValues {

    private final BillingMasterdataRepository billingMasterdataRepository;
    private final BillingConfigRepository billingConfigRepository;

    MailTemplateValues(BillingMasterdataRepository billingMasterdataRepository,
                       BillingConfigRepository billingConfigRepository) {
        this.billingMasterdataRepository = billingMasterdataRepository;
        this.billingConfigRepository = billingConfigRepository;
    }

    /**
     * The address of a test mail: {@code me} = the login's e-mail claim, {@code eeg} = the EEG
     * address from the master data; anything else is a 400.
     */
    String testRecipient(String tenant, String recipient, String loginEmail) {
        String to;
        if (MailTemplateService.RECIPIENT_ME.equals(recipient)) {
            to = singleAddress(loginEmail);
            if (to == null) {
                throw new ValidationException("recipient", "Für dein Login ist keine E-Mail-Adresse hinterlegt.");
            }
        } else if (MailTemplateService.RECIPIENT_EEG.equals(recipient)) {
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
        return to;
    }

    /** The first valid address of a ';'-separated value, or null. */
    private static String singleAddress(String raw) {
        return EmailAddressUtil.normalize(raw).stream().filter(EmailAddressUtil::isValid).findFirst().orElse(null);
    }

    /** The placeholder values of a billing document (issuer data of the document). */
    static Map<String, String> values(BillingDocument document) {
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
