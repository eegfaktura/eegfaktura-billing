package org.vfeeg.eegfaktura.billing.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.vfeeg.eegfaktura.billing.repos.BillingConfigRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingMailTemplateRepository;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.JwtAuthentication;
import org.vfeeg.eegfaktura.billing.security.JwtTokenService;
import org.vfeeg.eegfaktura.billing.service.MailTemplateCatalog;
import org.vfeeg.eegfaktura.billing.service.MailTemplateService;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup of the /api/mailtemplates end-to-end tests (security filter chain, Flyway V1_16 with
 * ddl validation, PostgreSQL). The JWT check itself is mocked; JwtTokenService has its own tests.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@SqlConfig(encoding = "UTF-8")
@Sql(scripts = "/billing_master_data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
abstract class MailTemplateIntegrationBase {

    /** TE100100 has EEG master data in billing_master_data.sql (eeg-holy-grail@gmx.at). */
    static final String TENANT_A = "TE100100";
    static final String TENANT_B = "TE200200";
    static final String BASE = "/api/mailtemplates/";
    static final String KEY = MailTemplateCatalog.BILLING_DOCUMENT;
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

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

    final ObjectMapper json = new ObjectMapper();

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

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token, String tenant) {
        return request.header("Authorization", "Bearer " + token).header("Tenant", tenant);
    }

    String body(Object... keyValues) throws Exception {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return json.writeValueAsString(map);
    }

    ResultActions putTemplate(String tenant, String subject, String bodyHtml, int version) throws Exception {
        return mvc.perform(as(put(BASE + KEY), "admin", tenant).contentType(MediaType.APPLICATION_JSON)
                .content(body("subject", subject, "bodyHtml", bodyHtml, "version", version)));
    }

    JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    String uploadImage(String tenant, byte[] data) throws Exception {
        return read(mvc.perform(as(multipart(BASE + KEY + "/files")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", data))
                        .param("kind", "image"), "admin", tenant))
                .andExpect(status().isCreated())).get("id").asText();
    }

    void cleanup(String tenant) throws Exception {
        mvc.perform(as(delete(BASE + KEY), "admin", tenant)).andExpect(status().isNoContent());
    }

    MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    static String raw(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
