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
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.vfeeg.eegfaktura.billing.domain.BillingConfig;
import org.vfeeg.eegfaktura.billing.domain.BillingMailTemplate;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
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
import java.util.UUID;

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

    @Autowired
    BillingConfigRepository billingConfigRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

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

    @Test
    void withoutTenantHeaderNothingIsAnswered() throws Exception {
        // the mail texts take the community only from the Tenant header; without it every handler refuses
        mvc.perform(get("/api/mailtemplates").header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + KEY).header("Authorization", "Bearer admin").header("Tenant", " "))
                .andExpect(status().isForbidden());
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
                .andExpect(jsonPath("$.bodyHtml").value("<p>Hallo <span data-ph=\"recipient.name\">Empfängername</span></p>"));

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
        // a block only on the top level
        putTemplate(TENANT_B, "x", "<ul><li>Kontakt: <div data-ph=\"eeg.contactBlock\">K</div></li></ul>", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(containsString("eeg.contactBlock")));
        putTemplate(TENANT_B, "x", "<p><span data-ph=\"eeg.contactBlock\">K</span></p>", 0)
                .andExpect(status().isBadRequest());
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
    @DisplayName("Parallel uploads are serialized: the 500 KB limit holds")
    void parallelUploadsKeepTheLimit() throws Exception {
        String tenant = "TE500500";
        when(jwtTokenService.validateTokenAndGetAuthentication("admin-p")).thenReturn(new JwtAuthentication("admin-p",
                List.of(new Authority(tenant), new Authority("ROLE_EEG_ADMIN"))));
        mvc.perform(as(delete(BASE + KEY), "admin-p", tenant)).andExpect(status().isNoContent());
        byte[] image = Arrays.copyOf(PNG, 200 * 1024);
        int parallel = 6;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(parallel);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < parallel; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(as(multipart(BASE + KEY + "/files")
                                .file(new MockMultipartFile("file", "p.png", "image/png", image))
                                .param("kind", "image"), "admin-p", tenant))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (var result : results) {
            created += result.get() == 201 ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(2, created, "2 x 200 KB fit into 500 KB, a third does not");
        assertEquals(2, read(mvc.perform(as(get(BASE + KEY), "admin-p", tenant))).get("files").size());
        mvc.perform(as(delete(BASE + KEY), "admin-p", tenant)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Drafts: PUT commits referenced images (also older drafts), refuses missing ones, keeps fresh drafts")
    void imagesInText() throws Exception {
        cleanup(TENANT_A);
        cleanup(TENANT_B);
        String own = uploadImage(TENANT_B, PNG);
        String unused = uploadImage(TENANT_B, PNG);
        String foreign = uploadImage(TENANT_A, PNG);

        JsonNode saved = read(putTemplate(TENANT_B, "x",
                "<p><img src=\"mailfile:" + own + "\" alt=\"Logo\" />"
                        + "<img src=\"https://tracker.example/x.png\" />Text</p>", 0)
                .andExpect(status().isOk()));
        assertEquals("<p><img src=\"mailfile:" + own + "\" alt=\"Logo\" />Text</p>", saved.get("bodyHtml").asText());
        assertEquals(2, saved.get("files").size());
        assertEquals(own, saved.get("files").get(0).get("id").asText());
        assertTrue(saved.get("files").get(0).get("committed").asBoolean());
        // a fresh draft (e.g. of another editor) stays a draft
        assertEquals(unused, saved.get("files").get(1).get("id").asText());
        assertFalse(saved.get("files").get(1).get("committed").asBoolean());
        assertTrue(saved.get("attachmentFileId").isNull());
        assertFalse(saved.get("legacyTemplateActive").asBoolean());

        // the saved version uses "own": DELETE is 409; a draft may be deleted
        mvc.perform(as(delete(BASE + KEY + "/files/" + own), "admin", TENANT_B))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1));
        String draft = uploadImage(TENANT_B, PNG);
        mvc.perform(as(delete(BASE + KEY + "/files/" + draft), "admin", TENANT_B)).andExpect(status().isNoContent());

        // a draft older than 24 h is no longer listed and is removed by the next save
        jdbcTemplate.update("update billing_mail_template_file set date_created = now() - interval '48 hours' where id = ?",
                UUID.fromString(unused));
        assertEquals(1, read(mvc.perform(as(get(BASE + KEY), "admin", TENANT_B))).get("files").size());
        // an image in the text whose file is gone: 400, never dropped silently
        putTemplate(TENANT_B, "x", "<p>a<img src=\"mailfile:" + UUID.randomUUID() + "\" /></p>", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Ein Bild im Text gibt es nicht (mehr); bitte erneut einfügen."));
        // an image of another tenant is not a file of this mail either
        putTemplate(TENANT_B, "x", "<p>a<img src=\"mailfile:" + foreign + "\" /></p>", 1)
                .andExpect(status().isBadRequest());
        // a referenced draft older than 24 h is still there: the save commits it
        String old = uploadImage(TENANT_B, PNG);
        jdbcTemplate.update("update billing_mail_template_file set date_created = now() - interval '48 hours' where id = ?",
                UUID.fromString(old));
        JsonNode withOld = read(putTemplate(TENANT_B, "x",
                "<p><img src=\"mailfile:" + own + "\" /><img src=\"mailfile:" + old + "\" /></p>", 1)
                .andExpect(status().isOk()));
        assertTrue(withOld.get("bodyHtml").asText().contains("mailfile:" + old));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "select committed from billing_mail_template_file where id = ?", Boolean.class, UUID.fromString(old)));
        // the next save without "own": the committed file goes, the stale draft too
        JsonNode second = read(putTemplate(TENANT_B, "x", "<p>ohne Bild</p>", 2).andExpect(status().isOk()));
        assertEquals(0, second.get("files").size());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from billing_mail_template_file where tenant_id = ?", Integer.class, TENANT_B));
        // no extra PDF for the billing mail
        mvc.perform(as(put(BASE + KEY), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>", "version", 3,
                                "attachmentFileId", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attachmentFileId"));
        mvc.perform(as(post(BASE + KEY + "/preview"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>", "attachmentFileId", null)))
                .andExpect(status().isOk());
        mvc.perform(as(post(BASE + KEY + "/preview"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "x", "bodyHtml", "<p>a</p>",
                                "attachmentFileId", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attachmentFileId"));

        // preview: own image as data: URI, sample member
        mvc.perform(as(post(BASE + KEY + "/preview"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "{document.type} für {recipient.name}",
                                "bodyHtml", "<p><img src=\"mailfile:" + uploadImage(TENANT_B, PNG) + "\" /></p>"
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
        // tenant B has no participants, so no row in base.billing_masterdata
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_B).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "eeg", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Für die Gemeinschaft ist keine E-Mail-Adresse bekannt."));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin", TENANT_A).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "attacker@example.com", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isBadRequest());
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Master data and billing config are found when their stored tenant case differs from the header")
    void tenantCaseInsensitiveLookups() throws Exception {
        String tenant = "TE400400";
        if (billingConfigRepository.findFirstByTenantIdIgnoreCase(tenant).isEmpty()) {
            jdbcTemplate.update("insert into base.billing_masterdata (tenant_id, eec_name, eec_email) values (?, ?, ?)",
                    "te400400", "EEG Kleinschreibung", "klein@example.com; kein-gueltiger ;obmann@example.com");
            BillingConfig config = new BillingConfig();
            config.setTenantId("te400400");
            config.setFooterText("Fuss <b>fett</b> &amp; mehr");
            billingConfigRepository.save(config);
        }
        when(jwtTokenService.validateTokenAndGetAuthentication("admin-c")).thenReturn(new JwtAuthentication("admin-c",
                List.of(new Authority(tenant), new Authority("ROLE_EEG_ADMIN")), false, "c@example.com"));

        mvc.perform(as(post(BASE + KEY + "/preview"), "admin-c", tenant).contentType(MediaType.APPLICATION_JSON)
                        .content(body("subject", "{eeg.name}", "bodyHtml",
                                "<p><span data-ph=\"billing.footerText\">F</span></p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("EEG Kleinschreibung"))
                .andExpect(jsonPath("$.html").value(containsString("Fuss <b>fett</b> &amp; mehr")));
        mvc.perform(as(post(BASE + KEY + "/test"), "admin-c", tenant).contentType(MediaType.APPLICATION_JSON)
                        .content(body("recipient", "eeg", "subject", "x", "bodyHtml", "<p>a</p>")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("klein@example.com; obmann@example.com"));
        assertEquals(2, sentMessage().getAllRecipients().length);
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
