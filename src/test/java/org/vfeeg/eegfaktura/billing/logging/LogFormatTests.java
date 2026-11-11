package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a log line looks like with {@code LOG_FORMAT=json}: {@link EcsJsonFormatter} on a built logback event, one ECS
 * JSON object per line with the common fields of the logging concept, and the start line of {@link StartupLog}.
 * No Spring start and no change to the global logging setup.
 */
class LogFormatTests {

    /** A logger context of its own: the events are built here, the global one stays untouched. */
    private static final LoggerContext CONTEXT = new LoggerContext();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static LoggingEvent event(Level level, String message, Throwable error, Map<String, String> mdc,
                                      Object... keyValues) {
        LoggingEvent event = new LoggingEvent(LogFormatTests.class.getName(),
                CONTEXT.getLogger("org.vfeeg.eegfaktura.billing.Sample"), level, message, error, null);
        event.setMDCPropertyMap(mdc);
        for (int i = 0; i < keyValues.length; i += 2) {
            event.addKeyValuePair(new KeyValuePair((String) keyValues[i], keyValues[i + 1]));
        }
        return event;
    }

    private static JsonNode json(MockEnvironment environment, ILoggingEvent event) throws Exception {
        String line = new EcsJsonFormatter(environment).format(event);
        assertTrue(line.endsWith("\n") && line.indexOf('\n') == line.length() - 1, "one line: " + line);
        return MAPPER.readTree(line);
    }

    private static String field(JsonNode json, String name) {
        JsonNode node = json.get(name);
        return node == null ? null : node.asText();
    }

    @Test
    void jsonLineCarriesTheCommonFields() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(LogSettings.SERVICE_VERSION_PROPERTY, "9.9.9");
        Map<String, String> mdc = Map.of(LogFields.REQUEST_ID, "req-1", LogFields.TENANT_ID, "RC100001",
                "user.email", "kassier@example.org"); // not on the whitelist
        JsonNode json = json(environment, event(Level.ERROR, "Abrechnung fehlgeschlagen",
                new IllegalStateException("broken \"quoted\"\nsecond line"), mdc,
                LogFields.EVENT_ACTION, "billing.run.failed", "http.request.method", "POST",
                "event.duration", 1_500_000L, "iban", "AT611904300234573201"));

        assertTrue(field(json, "@timestamp").matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z"),
                "UTC with milliseconds: " + field(json, "@timestamp"));
        assertEquals("error", field(json, "log.level"));
        assertEquals("org.vfeeg.eegfaktura.billing.Sample", field(json, "log.logger"));
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
        assertTrue(json.get("event.duration").isNumber(), "a number stays a number");
        assertEquals(1_500_000L, json.get("event.duration").asLong());
        // whitelist: neither an unknown MDC entry nor an unknown key-value pair is written
        assertNull(json.get("user.email"));
        assertNull(json.get("iban"));
        assertFalse(json.toString().contains("kassier@example.org") || json.toString().contains("AT6119"), "" + json);
    }

    @Test
    void messageErrorAndStackTraceAreMaskedAndIdsStay() throws Exception {
        String meteringPoint = "AT0030000000000000000000000000001";
        JsonNode json = json(new MockEnvironment(), event(Level.WARN, "mask anna@example.org at " + meteringPoint,
                new IllegalStateException("for bert@example.org",
                        new IllegalArgumentException("IBAN AT483200000012345864 password=s3cret")), Map.of()));

        assertEquals("mask <email> at " + meteringPoint, field(json, "message"));
        assertEquals("for <email>", field(json, "error.message"));
        assertTrue(field(json, "error.stack_trace").contains("Caused by: java.lang.IllegalArgumentException: "
                + "IBAN <iban> password=<masked>"), field(json, "error.stack_trace"));
        assertTrue(field(json, "error.stack_trace").contains("LogFormatTests"), "frames stay readable");
        for (String secret : List.of("anna@example.org", "bert@example.org", "AT483200000012345864", "s3cret")) {
            assertFalse(json.toString().contains(secret), secret + " in " + json);
        }
    }

    @Test
    void textLineHasTheSameFieldsMaskedOnOneLine() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(LogSettings.SERVICE_VERSION_PROPERTY, "9.9.9");
        String line = new EcsTextFormatter(environment).format(event(Level.WARN,
                "mail to anna@example.org\nforged line", new IllegalStateException("IBAN AT483200000012345864"),
                Map.of(LogFields.REQUEST_ID, "req-1", "user.email", "kassier@example.org"),
                "event.outcome", "refused", "iban", "AT611904300234573201"));

        assertTrue(line.endsWith("\n") && line.indexOf('\n') == line.length() - 1, "one line: " + line);
        assertTrue(line.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z WARN "
                + "org\\.vfeeg\\.eegfaktura\\.billing\\.Sample: mail to <email>\\\\nforged line .*\n"), line);
        for (String field : List.of(" service.name=eegfaktura-billing", " service.version=9.9.9", " request.id=req-1",
                " event.outcome=refused", " error.type=java.lang.IllegalStateException",
                " error.message=\"IBAN <iban>\"", " error.stack_trace=\"java.lang.IllegalStateException: IBAN <iban>\\")) {
            assertTrue(line.contains(field), field + " in " + line);
        }
        for (String dropped : List.of("kassier@example.org", "AT6119", "AT4832", "anna@example.org", "user.email")) {
            assertFalse(line.contains(dropped), dropped + " in " + line);
        }
    }

    @Test
    void textValuesWithBlanksQuotesOrControlCharactersAreQuotedAndEscaped() {
        String bell = String.valueOf((char) 7);
        String separator = String.valueOf((char) 0x2028);
        String line = new EcsTextFormatter(new MockEnvironment()).format(event(Level.INFO,
                "say \"hi\"" + separator + "x", null, Map.of(), "event.action", "a b", "http.route", "/a=\"b\"",
                "event.outcome", "c" + bell + "\r\\"));

        String backslash = "\\";
        assertTrue(line.contains(": say \"hi\"" + backslash + "u2028x "), line);
        assertTrue(line.contains(" event.action=\"a b\""), line);
        assertTrue(line.contains(" http.route=\"/a=\\\"b\\\"\""), line);
        assertTrue(line.contains(" event.outcome=c" + backslash + "u0007\\r\\\\"), line);
    }

    @Test
    void anExplicitErrorTypeWinsOverTheExceptionsType() throws Exception {
        JsonNode json = json(new MockEnvironment(), event(Level.ERROR, "x", new IllegalStateException("x"), Map.of(),
                "error.type", "org.postgresql.util.PSQLException"));
        assertEquals("org.postgresql.util.PSQLException", field(json, "error.type"));
    }

    @Test
    void deploymentEnvironmentIsOnTheLineWhenSet() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("DEPLOYMENT_ENVIRONMENT", " pilot ");
        new LogSettings().postProcessEnvironment(environment, null);

        assertEquals("pilot", field(json(environment, event(Level.INFO, "x", null, Map.of())),
                "deployment.environment"));
    }

    @Test
    void deploymentEnvironmentUnsetOrBlankLeavesTheFieldOut() throws Exception {
        for (MockEnvironment environment : List.of(new MockEnvironment(),
                new MockEnvironment().withProperty("DEPLOYMENT_ENVIRONMENT", " "))) {
            new LogSettings().postProcessEnvironment(environment, null);
            assertNull(json(environment, event(Level.INFO, "x", null, Map.of())).get("deployment.environment"));
        }
    }

    @Test
    void startLineWithVersionAndEffectiveLevel() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(LogSettings.SERVICE_VERSION_PROPERTY, "9.9.9");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Logger startup = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StartupLog.LOGGER);
        startup.addAppender(appender);
        try {
            new StartupLog().started();
        } finally {
            startup.detachAppender(appender);
        }

        assertEquals(1, appender.list.size(), "" + appender.list);
        ILoggingEvent line = appender.list.get(0);
        assertEquals(Level.INFO, line.getLevel());
        Map<String, Object> fields = new HashMap<>();
        line.getKeyValuePairs().forEach(kv -> fields.put(kv.key, kv.value));
        String root = ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
                .getEffectiveLevel().toString().toLowerCase(Locale.ROOT);
        assertEquals("service.start", fields.get(LogFields.EVENT_ACTION));
        assertEquals(root, fields.get(LogFields.LOG_EFFECTIVE_LEVEL));
        JsonNode json = json(environment, line);
        assertEquals("9.9.9", field(json, "service.version"));
        assertEquals("service start", field(json, "message"));
        assertEquals("info", field(json, "log.level"));
        assertEquals(root, field(json, "log.effective_level"));
    }

    /** The service picks {@link LogSettings} up the way Spring Boot loads every environment post-processor. */
    @Test
    void springFactoriesNamesLogSettings() throws Exception {
        boolean named = false;
        for (URL url : Collections.list(getClass().getClassLoader().getResources("META-INF/spring.factories"))) {
            Properties factories = new Properties();
            try (InputStream in = url.openStream()) {
                factories.load(in);
            }
            named |= factories.getProperty(EnvironmentPostProcessor.class.getName(), "")
                    .contains(LogSettings.class.getName());
        }
        assertTrue(named, "META-INF/spring.factories names " + LogSettings.class.getName());
    }
}
