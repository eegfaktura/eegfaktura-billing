package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The JSON line of the service (set by {@link LogSettings} for {@code LOG_FORMAT=json}): one object per line with
 * flat, dotted ECS names, the same shape as the other eegfaktura services (Spring Boot's own {@code ecs} format
 * nests the objects and adds process/ecs fields). {@code @timestamp} in UTC with milliseconds, {@code log.level} in
 * lower case. Of the MDC and the key-value pairs only the names in {@link #FIELDS} are written — anything else a
 * library or a call site adds is dropped (never-log rule).
 */
public class EcsJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

    /** Request and event fields taken from the MDC or the key-value pairs of a line. */
    static final Set<String> FIELDS = Set.of("event.action", "event.outcome", "event.duration",
            "http.request.method", "http.route", "http.response.status_code", "request.id", "trace.id", "span.id",
            "tenant.id", "client.id", "support.access", "error.type");

    private static final DateTimeFormatter MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final JsonWriter<Map<String, Object>> JSON = JsonWriter.standard();

    private final String serviceName;
    private final String serviceVersion;

    public EcsJsonFormatter(Environment environment) {
        this.serviceName = environment.getProperty(LogSettings.SERVICE_NAME_PROPERTY, "eegfaktura-billing");
        this.serviceVersion = environment.getProperty(LogSettings.SERVICE_VERSION_PROPERTY, "dev");
    }

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("@timestamp", MILLIS.format(event.getInstant()));
        line.put("log.level", event.getLevel().toString().toLowerCase(Locale.ROOT));
        line.put("log.logger", event.getLoggerName());
        line.put("service.name", serviceName);
        line.put("service.version", serviceVersion);
        event.getMDCPropertyMap().forEach((key, value) -> {
            if (FIELDS.contains(key)) {
                line.put(key, value);
            }
        });
        if (event.getKeyValuePairs() != null) {
            for (KeyValuePair pair : event.getKeyValuePairs()) {
                if (FIELDS.contains(pair.key) && pair.value != null) {
                    line.put(pair.key, pair.value instanceof Number ? pair.value : pair.value.toString());
                }
            }
        }
        IThrowableProxy error = event.getThrowableProxy();
        if (error != null) {
            line.put("error.type", error.getClassName());
            line.put("error.message", error.getMessage());
            line.put("error.stack_trace", ThrowableProxyUtil.asString(error));
        }
        line.put("message", event.getFormattedMessage());
        return JSON.writeToString(line) + "\n";
    }
}
