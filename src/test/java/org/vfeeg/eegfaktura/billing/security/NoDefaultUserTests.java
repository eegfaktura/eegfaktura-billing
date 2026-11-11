package org.vfeeg.eegfaktura.billing.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.vfeeg.eegfaktura.billing.rest.Endpoint;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;
import org.vfeeg.eegfaktura.billing.support.PostgresContainerHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The whole application, as it starts in production: Spring creates no default user (so it has no generated password
 * to log), and the login stays what it was.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NoDefaultUserTests {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresContainerHolder.register(registry);
        registry.add("app.jwt-public-key-file", () -> TestTokens.CERT_FILE.toString());
    }

    @LocalServerPort
    int port;
    @Autowired
    ApplicationContext context;

    private int status(String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/fileData/" + Endpoint.ID)).header("Tenant", TestTokens.OWN);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void noUserDetailsServiceBean() {
        assertEquals(0, context.getBeansOfType(UserDetailsService.class).size());
    }

    @Test
    void loginStaysBearerTokenOnly() throws Exception {
        assertEquals(403, status(null));
        assertEquals(403, status("Basic " + Base64.getEncoder().encodeToString("user:password".getBytes())));
        assertEquals(403, status(TestTokens.userWithoutAdminRole()));
        assertEquals(403, status(TestTokens.expired()));
        // through the chain to the resource, which knows no such file
        assertEquals(404, status(TestTokens.admin()));
    }
}
