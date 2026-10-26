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
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Runs the shared link cases (src/test/resources/mail-link-cases.json, byte-identical to the copy in
 * eegfaktura-backend): the error keys of the link rules, and the same body through the validation a
 * PUT, preview and test mail use.
 */
class MailLinkCasesTest {

    @TestFactory
    List<DynamicTest> sharedCases() throws Exception {
        JsonNode root;
        try (InputStream in = new ClassPathResource("mail-link-cases.json").getInputStream()) {
            root = new ObjectMapper().readTree(in);
        }
        MailHtmlPolicy policy = new MailHtmlPolicy();
        MailLinkRules rules = new MailLinkRules(policy);
        MailTemplateCatalog catalog = new MailTemplateCatalog(policy);
        MailTemplateService service = new MailTemplateService(catalog, policy, new MailTemplateRenderer(policy),
                mock(BillingMailTemplateRepository.class), mock(BillingMailTemplateFileRepository.class),
                mock(FileDataRepository.class), mock(BillingMasterdataRepository.class),
                mock(BillingConfigRepository.class), mock(EmailService.class), new AppProperties(),
                mock(EntityManager.class));
        MailTemplateCatalog.Entry entry = catalog.find(MailTemplateCatalog.BILLING_DOCUMENT).orElseThrow();

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.path("cases")) {
            String html = c.path("html").asText();
            boolean ok = c.path("ok").asBoolean(false);
            Set<String> expected = new TreeSet<>();
            c.path("errors").forEach(e -> expected.add(e.asText()));
            tests.add(DynamicTest.dynamicTest(c.path("name").asText(), () -> {
                assertTrue(ok != !expected.isEmpty(), "case needs ok or errors: " + c);
                String sanitized = policy.sanitize(html, Set.of());
                Set<String> keys = new TreeSet<>();
                rules.check(sanitized).forEach(v -> keys.add(v.key()));
                assertEquals(expected, keys, html);
                if (ok) {
                    assertDoesNotThrow(() -> service.validate(entry, "x", html, Set.of()), html);
                } else {
                    MailTemplateService.ValidationException e = assertThrows(
                            MailTemplateService.ValidationException.class,
                            () -> service.validate(entry, "x", html, Set.of()), html);
                    assertEquals(expected.size(), e.getErrors().size(), e.getMessage());
                    e.getErrors().forEach(err -> assertEquals("bodyHtml", err.field()));
                }
            }));
        }
        assertFalse(tests.isEmpty());
        return tests;
    }
}
