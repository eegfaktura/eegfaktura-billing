package org.vfeeg.eegfaktura.billing.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a log line looks like on stdout with {@code LOG_FORMAT=json}: one ECS JSON object per line, with the common
 * fields of the logging concept. Starts a bare Spring application, so {@link LogSettings} is picked up the way the
 * service picks it up (META-INF/spring.factories).
 */
@ExtendWith(OutputCaptureExtension.class)
class LogFormatTests {

    /** Plain source class, no component scan (stays out of the other tests' contexts). */
    static class Bare {
    }

    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(Bare.class).web(WebApplicationType.NONE).run(args);
    }

    @AfterAll
    static void backToText() {
        // the logging system is global: leave it as the other tests expect it. Boot keeps the structured format in a
        // system property once set (one start per JVM in production), so it is cleared here.
        System.clearProperty("CONSOLE_LOG_STRUCTURED_FORMAT");
        start("--LOG_FORMAT=text").close();
    }

    @Test
    void jsonLineCarriesTheCommonFields(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext ignored = start("--LOG_FORMAT=json", "--SERVICE_VERSION=9.9.9")) {
            Logger log = LoggerFactory.getLogger("org.vfeeg.eegfaktura.billing.Sample");
            MDC.put(LogFields.REQUEST_ID, "req-1");
            MDC.put(LogFields.TENANT_ID, "RC100001");
            MDC.put("user.email", "kassier@example.org"); // not on the whitelist
            try {
                log.atError().addKeyValue(LogFields.EVENT_ACTION, "billing.run.failed")
                        .addKeyValue("http.request.method", "POST").addKeyValue("event.duration", 1_500_000L)
                        .addKeyValue("iban", "AT611904300234573201")
                        .setCause(new IllegalStateException("broken \"quoted\"\nsecond line"))
                        .log("Abrechnung fehlgeschlagen");
            } finally {
                MDC.clear();
            }
        }
        String line = Arrays.stream(output.getOut().split("\\R"))
                .filter(l -> l.contains("Abrechnung fehlgeschlagen")).findFirst().orElseThrow();
        System.err.println("sample JSON line: " + line);
        JsonNode json = new ObjectMapper().readTree(line);

        assertTrue(json.at("/@timestamp").asText().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z"),
                "UTC with milliseconds: " + json.at("/@timestamp"));
        assertEquals("error", field(json, "log.level"));
        assertEquals("eegfaktura-billing", field(json, "service.name"));
        assertEquals("9.9.9", field(json, "service.version"));
        assertEquals("billing.run.failed", field(json, "event.action"));
        assertEquals("req-1", field(json, "request.id"));
        assertEquals("RC100001", field(json, "tenant.id"));
        assertEquals("Abrechnung fehlgeschlagen", field(json, "message"));
        assertEquals(IllegalStateException.class.getName(), field(json, "error.type"));
        assertEquals("broken \"quoted\"\nsecond line", field(json, "error.message"));
        assertTrue(field(json, "error.stack_trace").contains("LogFormatTests"), "stack trace inside the record");
        assertEquals("POST", field(json, "http.request.method"));
        assertEquals("1500000", field(json, "event.duration"));
        // whitelist: neither an unknown MDC entry or key-value pair nor Boot's extra fields are written
        for (String dropped : new String[]{"kassier@example.org", "AT611904300234573201", "\"process\"",
                "\"ecs\"", "\"node\""}) {
            assertFalse(line.contains(dropped), dropped + " in " + line);
        }
    }

    @Test
    void textFormatIsNotJson(CapturedOutput output) {
        System.clearProperty("CONSOLE_LOG_STRUCTURED_FORMAT");
        try (ConfigurableApplicationContext ignored = start("--LOG_FORMAT=text")) {
            LoggerFactory.getLogger("org.vfeeg.eegfaktura.billing.Sample").info("plain line");
        }
        String line = Arrays.stream(output.getOut().split("\\R"))
                .filter(l -> l.contains("plain line")).findFirst().orElseThrow();
        assertFalse(line.startsWith("{"), line);
    }

    /** ECS field by its dotted name, whether the encoder writes it flat ("a.b") or nested ({"a":{"b"}}). */
    private static String field(JsonNode json, String dotted) {
        JsonNode flat = json.get(dotted);
        if (flat != null) {
            return flat.asText();
        }
        JsonNode node = json;
        for (String part : dotted.split("\\.")) {
            node = node.path(part);
        }
        return node.isMissingNode() ? null : node.asText();
    }
}
