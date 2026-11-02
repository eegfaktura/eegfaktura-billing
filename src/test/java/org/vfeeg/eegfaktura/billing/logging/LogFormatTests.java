package org.vfeeg.eegfaktura.billing.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;

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

    @BeforeAll
    static void releaseTheLoggingOfEarlierTests() {
        // with a cached context of another test in this JVM, logging counts as set up and a start would not apply its
        // format; closing a context cleans it up, so every start below sets up logging itself
        start("--LOG_FORMAT=text").close();
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
    void messageErrorAndStackTraceAreMaskedAndIdsStay(CapturedOutput output) throws Exception {
        String meteringPoint = "AT0030000000000000000000000000001";
        try (ConfigurableApplicationContext ignored = start("--LOG_FORMAT=json")) {
            LoggerFactory.getLogger("org.vfeeg.eegfaktura.billing.Sample").atWarn()
                    .setCause(new IllegalStateException("for bert@example.org",
                            new IllegalArgumentException("IBAN AT483200000012345864 password=s3cret")))
                    .log("mask anna@example.org at " + meteringPoint);
        }
        String line = Arrays.stream(output.getOut().split("\\R"))
                .filter(l -> l.contains("IllegalStateException")).findFirst().orElseThrow();
        JsonNode json = new ObjectMapper().readTree(line);

        assertEquals("mask <email> at " + meteringPoint, field(json, "message"));
        assertEquals("for <email>", field(json, "error.message"));
        assertTrue(field(json, "error.stack_trace").contains("Caused by: java.lang.IllegalArgumentException: "
                + "IBAN <iban> password=<masked>"), field(json, "error.stack_trace"));
        assertTrue(field(json, "error.stack_trace").contains("LogFormatTests"), "frames stay readable");
        for (String secret : new String[]{"anna@example.org", "bert@example.org", "AT483200000012345864", "s3cret"}) {
            assertFalse(line.contains(secret), secret + " in " + line);
        }
    }

    @Test
    void deploymentEnvironmentIsOnEveryLine(CapturedOutput output) {
        try (ConfigurableApplicationContext ignored = start("--LOG_FORMAT=json", "--DEPLOYMENT_ENVIRONMENT=pilot")) {
            LoggerFactory.getLogger("org.vfeeg.eegfaktura.billing.Sample").info("sample line");
        }
        List<String> json = jsonLinesOfTheStart(output);
        assertTrue(json.stream().anyMatch(l -> l.contains("sample line")), "the line was captured");
        for (String line : json) {
            assertTrue(line.contains("\"deployment.environment\":\"pilot\""), line);
        }
    }

    @Test
    void deploymentEnvironmentUnsetOrBlankLeavesTheFieldOut(CapturedOutput output) {
        for (String args : new String[]{"--LOG_FORMAT=json", "--LOG_FORMAT=json --DEPLOYMENT_ENVIRONMENT= "}) {
            try (ConfigurableApplicationContext ignored = start(args.split(" "))) {
                LoggerFactory.getLogger("org.vfeeg.eegfaktura.billing.Sample").info("sample line");
            }
        }
        List<String> json = jsonLinesOfTheStart(output);
        assertTrue(json.stream().anyMatch(l -> l.contains("sample line")), "the line was captured");
        assertTrue(json.stream().noneMatch(l -> l.contains("deployment.environment")), output.getOut());
    }

    /** Without devtools' first line: it comes before the start sets up logging, so with the setup of the test before. */
    private static List<String> jsonLinesOfTheStart(CapturedOutput output) {
        return Arrays.stream(output.getOut().split("\\R"))
                .filter(l -> l.startsWith("{") && !l.contains("RestartApplicationListener")).toList();
    }

    @Test
    void oneStartLineWithVersionAndEffectiveLevelAtEveryLevel(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(Bare.class, StartupLog.class)
                .web(WebApplicationType.NONE).run("--LOG_FORMAT=json", "--LOG_LEVEL=error",
                        "--SERVICE_VERSION=9.9.9", "--MAIL_PASSWORD=s3cret")) {
            assertTrue(ignored.isRunning());
        }
        List<String> lines = Arrays.stream(output.getOut().split("\\R"))
                .filter(l -> l.contains("service.start")).toList();
        assertEquals(1, lines.size(), output.getOut());
        JsonNode json = new ObjectMapper().readTree(lines.get(0));
        assertEquals("info", field(json, "log.level"));
        assertEquals("Service started: version 9.9.9, log level error", field(json, "message"));
        assertFalse(output.getOut().contains("s3cret"));
    }

    @Test
    void textFormatIsNotJson(CapturedOutput output) {
        System.clearProperty("CONSOLE_LOG_STRUCTURED_FORMAT");
        try (ConfigurableApplicationContext ignored = start("--LOG_FORMAT=text")) {
            LoggerFactory.getLogger("org.vfeeg.eegfaktura.billing.Sample").atInfo()
                    .addKeyValue("event.outcome", "refused").log("plain line");
        }
        String line = Arrays.stream(output.getOut().split("\\R"))
                .filter(l -> l.contains("plain line")).findFirst().orElseThrow();
        assertFalse(line.startsWith("{"), line);
        assertTrue(line.contains("event.outcome=\"refused\""), "key-value pairs in the text line: " + line);
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
