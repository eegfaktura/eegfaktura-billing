package org.vfeeg.eegfaktura.billing.config;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.vfeeg.eegfaktura.billing.security.Authority;
import org.vfeeg.eegfaktura.billing.security.JwtAuthentication;
import org.vfeeg.eegfaktura.billing.security.JwtTokenService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Real HTTP requests against the started service, with a bearer token that the (mocked) token
 * service accepts as EEG admin of RC100100. The JWT check itself is tested elsewhere.
 */
@Testcontainers
abstract class SpringdocAccessTestBase {

    @Container
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:15-alpine")
            .withUsername("sa")
            .withPassword("sa")
            .withReuse(true);

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

    @MockitoBean
    JwtTokenService jwtTokenService;

    @LocalServerPort
    int port;

    @BeforeEach
    void eegAdminToken() {
        when(jwtTokenService.validateTokenAndGetAuthentication(anyString())).thenReturn(new JwtAuthentication(
                "admin", List.of(new Authority("RC100100"), new Authority("ROLE_EEG_ADMIN"))));
    }

    HttpResponse<String> getWithToken(final String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer x")
                .header("Tenant", "RC100100")
                .GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
