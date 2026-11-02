package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.vfeeg.eegfaktura.billing.rest.Endpoint;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;
import org.vfeeg.eegfaktura.billing.support.PostgresContainerHolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The access line in the real servlet container: the tenant refusal leaves the filter chain as an exception and is
 * answered on Tomcat's error dispatch — the line carries the status the client actually gets.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class AccessLogTomcatTests {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresContainerHolder.register(registry);
        registry.add("app.jwt-public-key-file", () -> TestTokens.CERT_FILE.toString());
    }

    @LocalServerPort
    int port;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        ((Logger) RequestLogFilter.ACCESS).addAppender(appender);
    }

    @AfterEach
    void detach() {
        ((Logger) RequestLogFilter.ACCESS).detachAppender(appender);
    }

    private HttpResponse<String> get(String path, String tenant, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Tenant", tenant);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> onlyLine() {
        List<ILoggingEvent> lines = appender.list;
        assertEquals(1, lines.size(), "one access line: " + lines);
        Map<String, Object> fields = new HashMap<>(lines.get(0).getMDCPropertyMap());
        for (KeyValuePair kv : lines.get(0).getKeyValuePairs()) {
            fields.put(kv.key, kv.value);
        }
        return fields;
    }

    @Test
    void foreignTenantLineHasTheStatusTheClientGets() throws Exception {
        HttpResponse<String> response = get("/api/fileData/" + Endpoint.ID, TestTokens.FOREIGN, TestTokens.admin());

        Map<String, Object> f = onlyLine();
        assertEquals(response.statusCode(), f.get("http.response.status_code"), "line = what the client got");
        assertEquals(403, response.statusCode());
        assertEquals("refused", f.get("event.outcome"));
        assertNull(f.get("http.route"), "refused before the router; never the error page's /error");
        assertNull(f.get("tenant.id"), "a refused tenant is not logged as the tenant worked on");
        assertEquals(response.headers().firstValue(RequestLogFilter.REQUEST_ID_HEADER).orElseThrow(),
                f.get("request.id"));
    }

    /** The refusal leaves the chain as an exception: not its line, not its stack trace may quote the raw header. */
    @Test
    void refusedTenantHeaderIsNowhereInTheLog(CapturedOutput output) throws Exception {
        String header = "ZZ-REFUSED-HEADER";

        assertEquals(403, get("/api/fileData/" + Endpoint.ID, header, TestTokens.admin()).statusCode());

        assertTrue(output.getAll().contains("Token does not grant the requested tenant"), "the refusal was captured");
        assertFalse(output.getAll().contains(header), output.getAll());
    }

    @Test
    void valuesOfAFailedTenantComparisonAreNowhereInTheLog(CapturedOutput output) throws Exception {
        String header = "max muster";
        String path = "ZZ-PATH-TENANT";

        get("/api/billingConfigs/tenant/" + path, header, TestTokens.superuserAdmin());

        assertTrue(output.getAll().contains("does not match the tenant of the request"), "the refusal was captured");
        assertFalse(output.getAll().contains(header), output.getAll());
        assertFalse(output.getAll().contains(path), output.getAll());
    }

    @Test
    void missingTokenIsOneRefusedLine() throws Exception {
        HttpResponse<String> response = get("/api/fileData/" + Endpoint.ID, TestTokens.OWN, null);

        Map<String, Object> f = onlyLine();
        assertEquals(response.statusCode(), f.get("http.response.status_code"));
        assertEquals("refused", f.get("event.outcome"));
    }
}
