package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vfeeg.eegfaktura.billing.model.FileDataDTO;
import org.vfeeg.eegfaktura.billing.rest.Endpoint;
import org.vfeeg.eegfaktura.billing.rest.FileDataResource;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;
import org.vfeeg.eegfaktura.billing.rest.WebSliceTest;
import org.vfeeg.eegfaktura.billing.security.JwtRequestFilter;
import org.vfeeg.eegfaktura.billing.service.FileDataService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.FOREIGN;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/**
 * The access line through the real router and security chain: one line per request, the route template instead of
 * the path, tenant and client only after the token check, and nothing about the person (no user tracking,
 * decided 2026-10-29).
 */
@WebMvcTest(controllers = FileDataResource.class)
class AccessLogWebTests extends WebSliceTest {

    /** Every field the access line may carry (key-value pairs and MDC); anything else fails the never-log test. */
    private static final Set<String> ALLOWED = Set.of("event.action", "event.outcome", "http.request.method",
            "http.route", "http.response.status_code", "event.duration", "error.type",
            "request.id", "trace.id", "span.id", "tenant.id", "client.id");
    private static final String EMAIL = "kassier@example.org";
    private static final String CLIENT = "eegfaktura-web";
    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    FileDataService fileDataService;

    record Line(String message, Map<String, Object> fields) {
    }

    private final List<Line> lines = new ArrayList<>();
    // copies MDC and key-value pairs when the line is written (the MDC is cleared right after)
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            Map<String, Object> fields = new HashMap<>(event.getMDCPropertyMap());
            if (event.getKeyValuePairs() != null) {
                for (KeyValuePair kv : event.getKeyValuePairs()) {
                    fields.put(kv.key, kv.value);
                }
            }
            lines.add(new Line(event.getFormattedMessage(), fields));
        }
    };

    @BeforeEach
    void attach() {
        appender.start();
        ((Logger) RequestLogFilter.ACCESS).addAppender(appender);
        FileDataDTO file = new FileDataDTO();
        file.setTenantId(OWN);
        file.setName("a.pdf");
        file.setMimeType("application/pdf");
        file.setData(new byte[]{1});
        when(fileDataService.get(Endpoint.ID)).thenReturn(file);
    }

    @AfterEach
    void detach() {
        ((Logger) RequestLogFilter.ACCESS).detachAppender(appender);
    }

    private Line onlyLine() {
        assertEquals(1, lines.size(), "one access line per request: " + lines);
        return lines.get(0);
    }

    @Test
    void authenticatedRequestGivesOneLineWithTemplateTenantAndClient() throws Exception {
        String token = TestTokens.adminOfClient(CLIENT, EMAIL);
        mvc.perform(get("/api/fileData/" + Endpoint.ID + "?secret=query-value")
                        .header("Tenant", OWN.toLowerCase()).header(HttpHeaders.AUTHORIZATION, token)
                        .header("X-Request-Id", "req-42")
                        .header("traceparent", "00-" + TRACE + "-00f067aa0ba902b7-01")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 Firefox")
                        .with(r -> {
                            r.setRemoteAddr("203.0.113.7");
                            return r;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-42"));

        Line line = onlyLine();
        Map<String, Object> f = line.fields();
        assertEquals("http.access", f.get("event.action"));
        assertEquals("success", f.get("event.outcome"));
        assertEquals("GET", f.get("http.request.method"));
        assertEquals("/api/fileData/{id}", f.get("http.route"));
        assertEquals(200, f.get("http.response.status_code"));
        assertEquals("req-42", f.get("request.id"));
        assertEquals(TRACE, f.get("trace.id"));
        assertEquals("00f067aa0ba902b7", f.get("span.id"));
        assertEquals(OWN, f.get("tenant.id"));
        assertEquals(CLIENT, f.get("client.id"));
        assertTrue(f.get("event.duration") instanceof Long d && d > 0, "duration in nanoseconds");

        // never-log: only known fields, and none carries the person, the token, the id in the path, the query,
        // the IP or the user agent
        assertTrue(ALLOWED.containsAll(f.keySet()), "unexpected fields: " + f.keySet());
        String everything = line.message() + " " + f.values();
        for (String forbidden : List.of(EMAIL, "test-admin", token.substring(7, 40), Endpoint.ID.toString(),
                "query-value", "secret", "203.0.113.7", "Firefox")) {
            assertFalse(everything.contains(forbidden), "access line contains " + forbidden + ": " + everything);
        }
        Map<String, String> mdcAfter = MDC.getCopyOfContextMap();
        assertTrue(mdcAfter == null || mdcAfter.isEmpty(), "MDC cleared after the request: " + mdcAfter);
    }

    @Test
    void requestWithoutTokenIsLoggedAsRefusedWithoutTenantOrClient() throws Exception {
        mvc.perform(get("/api/fileData/" + Endpoint.ID).header("Tenant", OWN)).andExpect(status().isForbidden());

        Map<String, Object> f = onlyLine().fields();
        assertEquals("refused", f.get("event.outcome"));
        assertEquals(403, f.get("http.response.status_code"));
        assertFalse(f.containsKey("http.route"), "no route for a request the router never saw");
        assertNull(f.get("tenant.id"), "tenant only after the token check");
        assertNull(f.get("client.id"));
    }

    @Test
    void foreignTenantIsLoggedAsRefusedWithoutThatTenant() {
        assertThrows(AccessDeniedException.class, () -> mvc.perform(get("/api/fileData/" + Endpoint.ID)
                .header("Tenant", FOREIGN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin())));

        Map<String, Object> f = onlyLine().fields();
        assertEquals("refused", f.get("event.outcome"));
        assertEquals(403, f.get("http.response.status_code"));
        assertEquals(AccessDeniedException.class.getName(), f.get("error.type"));
        assertNull(f.get("tenant.id"), "a refused tenant is not the tenant the request worked on");
    }

    /** The refusal warning of JwtRequestFilter names the tenant, never the user (no user tracking). */
    @Test
    void foreignTenantWarningNamesNoUser() {
        Logger filterLog = (Logger) org.slf4j.LoggerFactory.getLogger(JwtRequestFilter.class);
        filterLog.addAppender(appender);
        try {
            assertThrows(AccessDeniedException.class, () -> mvc.perform(get("/api/fileData/" + Endpoint.ID)
                    .header("Tenant", FOREIGN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin())));
        } finally {
            filterLog.detachAppender(appender);
        }
        String warning = lines.stream().map(Line::message).filter(m -> m.contains(FOREIGN)).findFirst().orElseThrow();
        assertFalse(warning.contains("test-admin"), warning);
    }

    @Test
    void invalidTraceparentIsIgnored() throws Exception {
        for (String invalid : List.of("ff-" + TRACE + "-00f067aa0ba902b7-01", "00-" + "0".repeat(32) + "-00f067aa0ba902b7-01",
                "00-" + TRACE + "-0000000000000000-01", "00-" + TRACE.toUpperCase() + "-00f067aa0ba902b7-01")) {
            lines.clear();
            mvc.perform(get("/api/fileData/" + Endpoint.ID).header("Tenant", OWN)
                    .header(HttpHeaders.AUTHORIZATION, TestTokens.admin()).header("traceparent", invalid));
            assertFalse(onlyLine().fields().containsKey("trace.id"), invalid);
        }
    }

    @Test
    void unusableRequestIdIsReplaced() throws Exception {
        mvc.perform(get("/api/fileData/" + Endpoint.ID).header("Tenant", OWN)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.admin())
                        .header("X-Request-Id", "not an id, \"quoted\""))
                .andExpect(status().isOk());

        Object id = onlyLine().fields().get("request.id");
        assertNotEquals("not an id, \"quoted\"", id);
        assertTrue(id.toString().matches("[0-9a-f]{32}"), "generated UUID, 32 hex: " + id);
    }
}
