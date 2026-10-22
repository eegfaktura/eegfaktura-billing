package org.vfeeg.eegfaktura.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.ClassPathResource;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateFileRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Runs the shared sanitizer cases (src/test/resources/mail-html-policy-cases.json, byte-identical to the
 * copy in eegfaktura-backend) through the same validation a PUT uses.
 */
class MailHtmlPolicyCasesTest {

    @TestFactory
    List<DynamicTest> sharedCases() throws Exception {
        JsonNode root;
        try (InputStream in = new ClassPathResource("mail-html-policy-cases.json").getInputStream()) {
            root = new ObjectMapper().readTree(in);
        }
        String ownImageId = root.path("context").path("ownImageId").asText();

        MailHtmlPolicy policy = new MailHtmlPolicy();
        MailTemplateService service = new MailTemplateService(new MailTemplateCatalog(policy), policy,
                new MailTemplateRenderer(policy), mock(BillingMailTemplateRepository.class),
                mock(BillingMailTemplateFileRepository.class), mock(FileDataRepository.class),
                mock(BillingMasterdataRepository.class), mock(BillingConfigRepository.class),
                mock(EmailService.class), new AppProperties(), mock(EntityManager.class));
        // the catalog the case file describes: inline eeg.name and eeg.email, block eeg.contactBlock
        MailTemplateCatalog.Entry entry = new MailTemplateCatalog.Entry("cases", "Cases", "", 1, "x", "<p>x</p>",
                List.of(new MailTemplateCatalog.Placeholder("eeg.name", "Name der EEG", false),
                        new MailTemplateCatalog.Placeholder("eeg.email", "E-Mail der EEG", false),
                        new MailTemplateCatalog.Placeholder("eeg.contactBlock", "Kontaktdaten der EEG", true)),
                new MailTemplateCatalog.Limits(3, 500 * 1024, 0, 0, 500 * 1024));

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.path("cases")) {
            String input = c.path("input").asText();
            tests.add(DynamicTest.dynamicTest(c.path("name").asText(), () -> {
                if (c.path("reject").asBoolean(false)) {
                    assertThrows(MailTemplateService.ValidationException.class,
                            () -> service.validate(entry, "x", input, Set.of(ownImageId)), input);
                    return;
                }
                String out = service.validate(entry, "x", input, Set.of(ownImageId)).bodyHtml();
                for (JsonNode s : c.path("contains")) {
                    assertTrue(out.contains(s.asText()), "expected '" + s.asText() + "' in: " + out);
                }
                for (JsonNode s : c.path("notContains")) {
                    assertFalse(out.contains(s.asText()), "unexpected '" + s.asText() + "' in: " + out);
                }
            }));
        }
        assertFalse(tests.isEmpty());
        return tests;
    }
}
