package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.core.env.Environment;

import java.util.Locale;
import java.util.Map;

/**
 * The text line of the service ({@code LOG_FORMAT=text}, set by {@link LogSettings}): the same filtered and masked
 * fields as {@link EcsJsonFormatter}, as one plain line — {@code @timestamp LEVEL logger: message key=value ...}.
 * Control characters are escaped as in JSON, so a value never starts a new line; a value with a blank, a quote or an
 * {@code =} is quoted.
 */
public class EcsTextFormatter extends EcsJsonFormatter {

    public EcsTextFormatter(Environment environment) {
        super(environment);
    }

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> fields = fields(event);
        StringBuilder line = new StringBuilder();
        line.append(fields.remove("@timestamp")).append(' ')
                .append(fields.remove("log.level").toString().toUpperCase(Locale.ROOT)).append(' ')
                .append(fields.remove("log.logger")).append(": ");
        escape(String.valueOf(fields.remove("message")), false, line);
        fields.forEach((key, value) -> {
            line.append(' ').append(key).append('=');
            String text = String.valueOf(value);
            boolean quoted = text.isEmpty() || text.chars().anyMatch(c -> c == ' ' || c == '"' || c == '=');
            if (quoted) {
                line.append('"');
            }
            escape(text, quoted, line);
            if (quoted) {
                line.append('"');
            }
        });
        return line.append('\n').toString();
    }

    /** Backslash, control and line-separator characters escaped as JSON escapes them; a quote only inside quotes. */
    private static void escape(String text, boolean quoted, StringBuilder out) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append(quoted ? "\\\"" : "\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isISOControl(c) || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }
}
