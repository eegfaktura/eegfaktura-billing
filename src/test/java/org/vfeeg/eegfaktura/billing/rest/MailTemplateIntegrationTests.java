package org.vfeeg.eegfaktura.billing.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateRepository;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.JwtAuthentication;
import org.vfeeg.eegfaktura.billing.security.JwtTokenService;
import org.vfeeg.eegfaktura.billing.service.MailTemplateCatalog;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/mailtemplates end to end (security filter chain, Flyway V1_16 with ddl validation, PostgreSQL):
 * contract of section H, tenant isolation, role, limits, preview and test mail.
 * The JWT check itself is mocked; JwtTokenService has its own tests.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@SqlConfig(encoding = "UTF-8")
@Sql(scripts = "/billing_master_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class MailTemplateIntegrationTests {

    /** TE100100 has EEG master data in billing_master_data.sql (eeg-holy-grail@gmx.at). */
    private static final String TENANT_A = "TE100100";
    private static final String TENANT_B = "TE200200";
    private static final String BASE = "/api/mailtemplates/";
    private static final String KEY = MailTemplateCatalog.BILLING_DOCUMENT;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    // started here, not via @Container: the class-level @Sql below runs before the Testcontainers
    // extension would start it
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withUsername("sa")
            .withPassword("sa")
            .withReuse(true);

    static {
        postgreSQLContainer.start();
    }

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    BillingMailTemplateRepository templateRepository;

    @Autowired
    MailTemplateService mailTemplateService;

    @MockitoBean
    JwtTokenService jwtTokenService;

    @MockitoBean
    JavaMailSender javaMailSender;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void tokens() {
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        // admin of A and B (both tenants in the token), with e-mail claim
        when(jwtTokenService.validateTokenAndGetAuthentication("admin")).thenReturn(new JwtAuthentication("admin",
                List.of(new Authority(TENANT_A), new Authority(TENANT_B), new Authority("ROLE_EEG_ADMIN")),
                false, "admin@example.com"));
        when(jwtTokenService.validateTokenAndGetAuthentication("admin-no-mail")).thenReturn(new JwtAuthentication(
                "admin2", List.of(new Authority(TENANT_A), new Authority("ROLE_EEG_ADMIN"))));
        when(jwtTokenService.validateTokenAndGetAuthentication("user")).thenReturn(new JwtAuthentication("user",
                List.of(new Authority(TENANT_A), new Authority("ROLE_EEG_USER"))));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token, String tenant) {
        return request.header("Authorization", "Bearer " + token).header("Tenant", tenant);
    }

    private String body(Object... keyValues) throws Exception {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return json.writeValueAsString(map);
    }

    private ResultActions putTemplate(String tenant, String subject, String bodyHtml, int version) throws Exception {
        return mvc.perform(as(put(BASE + KEY), "admin", tenant).contentType(MediaType.APPLICATION_JSON)
                .content(body("subject", subject, "bodyHtml", bodyHtml, "version", version)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String uploadImage(String tenant, byte[] data) throws Exception {
        return read(mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", data))
                        .param("kind", "image"), "admin", tenant))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private void cleanup(String tenant) throws Exception {
        mvc.perform(as(delete(BASE + KEY), "admin", tenant)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Without a stored text: list and GET show the default (customized=false, version 0)")
    void defaultTemplate() throws Exception {
        cleanup(TENANT_B);
        mvc.perform(as(get("/api/mailtemplates"), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value(KEY))
                .andExpect(jsonPath("$[0].customized").value(false))
                .andExpect(jsonPath("$[0].defaultChanged").value(false));
        mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.subject").value("{document.type} {document.period} - {eeg.name}"))
                .andExpect(jsonPath("$.bodyHtml").value(containsString("data-ph=\"recipient.name\"")))
                .andExpect(jsonPath("$.bodyHtml").value(containsString("src=\"mailfile:eegfaktura-logo\"")))
                .andExpect(jsonPath("$.files.length()").value(0))
                .andExpect(jsonPath("$.limits.maxImages").value(3))
                .andExpect(jsonPath("$.limits.maxAttachments").value(0))
                .andExpect(jsonPath("$.placeholders[?(@.key=='eeg.contactBlock')].block").value(true));
    }

    @Test
    @DisplayName("PUT saves (sanitized), increments the version; a stale version is 409; DELETE resets")
    void saveConflictReset() throws Exception {
        cleanup(TENANT_B);
        putTemplate(TENANT_B, "Beleg {document.type}", "<p onclick=\"x()\">Hallo <span data-ph=\"recipient.name\">N</span></p><script>alert(1)</script>", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedBy").value("admin"))
                .andExpect(jsonPath("$.bodyHtml").value("<p>Hallo <span data-ph=\"recipient.name\">N</span></p>"));

        putTemplate(TENANT_B, "Neu", "<p>neu</p>", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.message").exists());
        putTemplate(TENANT_B, "Neu", "<p>neu</p>", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(as(get("/api/mailtemplates"), "admin", TENANT_B))
                .andExpect(jsonPath("$[0].customized").value(true))
                .andExpect(jsonPath("$[0].updatedBy").value("admin"));

        cleanup(TENANT_B);
        mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("Validation: unknown placeholder, empty text, long subject, missing version are 400 with field errors")
    void validation() throws Exception {
        putTemplate(TENANT_B, "x", "<p><span data-ph=\"member.iban\">IBAN</span></p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bodyHtml"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("member.iban")));
        putTemplate(TENANT_B, "x {eeg.iban}", "<p>a</p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
        putTemplate(TENANT_B, "x", "<script>alert(1)</script><p> </p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bodyHtml"));
        putTemplate(TENANT_B, "x".repeat(201), "<p>a</p>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
        mvc.perform(as(put(BASE + KEY), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        mvc.perform(as(put(BASE + KEY), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(get(BASE + "consent-request"), "admin", TENANT_B)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Files: PNG 201 and served with the checked type; disguised file, attachment, 4th image, size are 400")
    void files() throws Exception {
        cleanup(TENANT_B);
        String id = uploadImage(TENANT_B, PNG);
        mvc.perform(as(get(BASE + KEY + "/files/" + id), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));

        // a text file named .png with image/png
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "x.png", "image/png", "<svg onload=alert(1)>".getBytes()))
                        .param("kind", "image"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        // no extra PDF for the billing mail
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("kind", "attachment"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("kind"));
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                        .param("kind", "video"), "admin", TENANT_B))
                .andExpect(status().isBadRequest());

        // together <= 500 KB
        byte[] big = Arrays.copyOf(PNG, 400 * 1024);
        uploadImage(TENANT_B, big);
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "c.png", "image/png", Arrays.copyOf(PNG, 120 * 1024)))
                        .param("kind", "image"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("500 KB")));
        // at most 3 images
        uploadImage(TENANT_B, PNG);
        mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "d.png", "image/png", PNG))
                        .param("kind", "image"), "admin", TENANT_B))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("3 Bilder")));

        mvc.perform(as(delete(BASE + KEY + "/files/" + id), "admin", TENANT_B)).andExpect(status().isNoContent());
        mvc.perform(as(get(BASE + KEY + "/files/" + id), "admin", TENANT_B)).andExpect(status().isNotFound());
        mvc.perform(as(get(BASE + KEY + "/files/not-a-uuid"), "admin", TENANT_B)).andExpect(status().isBadRequest());
        // built-in eegfaktura logo: served, not in files, not deletable
        mvc.perform(as(get(BASE + KEY + "/files/eegfaktura-logo"), "admin", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(as(delete(BASE + KEY + "/files/eegfaktura-logo"), "admin", TENANT_B)).andExpect(status().isNotFound());
        cleanup(TENANT_B);
        assertEquals(0, read(mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))).get("files").size());
    }

    @Test
    @DisplayName("PUT keeps referenced own images, drops foreign/unknown ones and deletes unreferenced files")
    void imagesInText() throws Exception {
        cleanup(TENANT_A);
        cleanup(TENANT_B);
        String own = uploadImage(TENANT_B, PNG);
        String unused = uploadImage(TENANT_B, PNG);
        String foreign = uploadImage(TENANT_A, PNG);

        JsonNode saved = read(putTemplate(TENANT_B, "x",
                "<p><img src=\"mailfile:" + own + "\" alt=\"Logo\" /><img src=\"mailfile:" + foreign + "\" />"
                        + "<img src=\"https://tracker.example/x.png\" />Text</p>", 0)
                .andExpect(status().isOk()));
        assertEquals("<p><img src=\"mailfile:" + own + "\" alt=\"Logo\" />Text</p>", saved.get("bodyHtml").asText());
        assertEquals(1, saved.get("files").size());
        assertEquals(own, saved.get("files").get(0).get("id").asText());
        mvc.perform(as(get(BASE + KEY + "/files/" + unused), "admin", TENANT_B)).andExpect(status().isNotFound());

        // preview: own image as data: URI, sample member
        mvc.perform(as(post(BASE + KEY + "/preview"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "{document.type} für {recipient.name}",
                                "bodyHtml", saved.get("bodyHtml").asText()
                                        + "<p><span data-ph=\"recipient.name\">x</span></p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Rechnung für Max Mustermann"))
                .andExpect(jsonPath("$.html").value(containsString("src=\"data:image/png;base64,")))
                .andExpect(jsonPath("$.html").value(containsString("Max Mustermann")))
                .andExpect(jsonPath("$.html").value(not(containsString("mailfile:"))));
        cleanup(TENANT_A);
        cleanup(TENANT_B);
    }

    @Test
    @DisplayName("Tenant isolation: files and texts of another tenant are 403 (load, then check), wrong header 403")
    void tenantIsolation() throws Exception {
        cleanup(TENANT_A);
        String fileOfA = uploadImage(TENANT_A, PNG);

        // tenant B is in the token, the file belongs to A: loaded, then refused
        mvc.perform(as(get(BASE + KEY + "/files/" + fileOfA), "admin", TENANT_B)).andExpect(status().isForbidden());
        mvc.perform(as(delete(BASE + KEY + "/files/" + fileOfA), "admin", TENANT_B)).andExpect(status().isForbidden());
        // the user of A only: B is not in the token
        // (JwtRequestFilter refuses before any controller; it throws out of the filter chain)
        assertThrows(AccessDeniedException.class, () ->
                mvc.perform(as(get(BASE + KEY + "/files/" + fileOfA), "admin-no-mail", TENANT_B)));
        mvc.perform(as(get(BASE + KEY + "/files/" + fileOfA), "admin-no-mail", TENANT_A)).andExpect(status().isOk());
        cleanup(TENANT_A);
    }

    @Test
    @DisplayName("Without EEG_ADMIN or without token: 403 / 401")
    void roleRequired() throws Exception {
        int noToken = mvc.perform(get("/api/mailtemplates").header("Tenant", TENANT_A)).andReturn().getResponse().getStatus();
        assertTrue(noToken == 401 || noToken == 403, "status " + noToken);
        mvc.perform(as(get("/api/mailtemplates"), "user", TENANT_A)).andExpect(status().isForbidden());
        mvc.perform(as(put(BASE + KEY), "user", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                .content(body("subject", "x", "bodyHtml", "<p>a</p>", "version", 0))).andExpect(status().isForbidden());
        assertTrue(templateRepository.findByTenantIdAndMailKey(TENANT_A, KEY).isEmpty());
    }

    @Test
    @DisplayName("Test mail: me = e-mail claim, eeg = master data address, [Test] prefix, no cc, real EEG data")
    void testMail() throws Exception {
        cleanup(TENANT_A);
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "me", "subject", "{document.type} - {eeg.name}",
                                "bodyHtml", "<p>Hallo <span data-ph=\"recipient.name\">x</span>, <span data-ph=\"eeg.email\">x</span></p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("admin@example.com"));
        MimeMessage message = sentMessage();
        assertEquals("[Test] Rechnung - Energiegemeinschaft Holy Grail", message.getSubject());
        assertEquals("admin@example.com", message.getAllRecipients()[0].toString());
        assertEquals(1, message.getAllRecipients().length);
        assertNull(message.getRecipients(MimeMessage.RecipientType.CC));
        String raw = raw(message);
        assertTrue(raw.contains("Max Mustermann"), raw);
        assertTrue(raw.contains("eeg-holy-grail") , raw);

        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "eeg", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("eeg-holy-grail@gmx.at"));
        Address[] to = sentMessage().getAllRecipients();
        assertEquals("eeg-holy-grail@gmx.at", to[0].toString());

        // the mail server refuses: 502 with a message
        reset(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("down"))
                .when(javaMailSender).send(any(MimeMessage.class));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "me", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").exists());

        reset(javaMailSender);
        // no e-mail claim; tenant B has no master data; free recipients are not possible
        mvc.perform(as(post(BASE + KEY + "/test"), "admin-no-mail", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "me", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipient"));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "eeg", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "attacker@example.com", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest());
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Send run: a stored text that cannot be rendered falls back to the default")
    void prepareForSendFallsBack() {
        String tenant = "TE300300";
        templateRepository.findByTenantIdAndMailKey(tenant, KEY).ifPresent(templateRepository::delete);
        BillingMailTemplate broken = new BillingMailTemplate();
        broken.setTenantId(tenant);
        broken.setMailKey(KEY);
        broken.setSubject("x");
        broken.setBodyHtml("<p><span data-ph=\"member.removed\">x</span></p>");
        broken.setVersion(1);
        broken.setDefaultVersion(1);
        templateRepository.save(broken);

        MailTemplateService.Prepared prepared = mailTemplateService.prepareForSend(tenant.toLowerCase());
        assertFalse(prepared.customized());
        assertTrue(prepared.failure() != null);
        assertEquals(prepared.entry().defaultBodyHtml(), prepared.bodyHtml());

        templateRepository.delete(templateRepository.findByTenantIdAndMailKey(tenant, KEY).orElseThrow());
        assertTrue(mailTemplateService.prepareForSend(tenant).failure() == null);
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    private static String raw(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
