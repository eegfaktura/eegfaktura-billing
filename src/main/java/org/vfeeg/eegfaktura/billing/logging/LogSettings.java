package org.vfeeg.eegfaktura.billing.logging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one logging switch of the service (logging concept W-261008-ca2, point 0/1). Runs before Spring Boot sets up
 * logging and maps the environment onto Boot's own properties:
 * <ul>
 *   <li>{@code LOG_FORMAT=json} (default) → Boot's structured console logging with {@link EcsJsonFormatter}: one
 *       JSON object per line on stdout, flat ECS names; {@code LOG_FORMAT=text} → {@link EcsTextFormatter}: the
 *       same fields, masked the same way, as one plain line.</li>
 *   <li>{@code LOG_LEVEL=trace|debug|info|warn|error} (default {@code info}; {@code warning} counts as {@code warn})
 *       → {@code logging.level.root}; the
 *       access logger and {@link StartupLog} stay at {@code info}, so {@code warn}/{@code error} keep those lines.</li>
 *   <li>{@code SERVICE_VERSION} (set in the image, default {@code dev}) → {@code service.version};
 *       {@code service.name} is {@code eegfaktura-billing}.</li>
 *   <li>{@code DEPLOYMENT_ENVIRONMENT} → {@code deployment.environment}; unset, the field is left out.</li>
 * </ul>
 * An unknown value of either stops the start. A per-package level stays Boot's own
 * {@code LOGGING_LEVEL_<PACKAGE>} (e.g. {@code LOGGING_LEVEL_ORG_VFEEG=debug}).
 */
public class LogSettings implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "eegfakturaLogSettings";
    static final String SERVICE_NAME_PROPERTY = "eegfaktura.logging.service-name";
    static final String SERVICE_VERSION_PROPERTY = "eegfaktura.logging.service-version";
    static final String DEPLOYMENT_ENVIRONMENT_PROPERTY = "eegfaktura.logging.deployment-environment";
    static final Set<String> LEVELS = Set.of("trace", "debug", "info", "warn", "error");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = resolve(environment.getProperty("LOG_FORMAT"),
                environment.getProperty("LOG_LEVEL"));
        properties.put(SERVICE_VERSION_PROPERTY, environment.getProperty("SERVICE_VERSION", "dev"));
        String deployment = environment.getProperty("DEPLOYMENT_ENVIRONMENT");
        if (deployment != null && !deployment.isBlank()) {
            properties.put(DEPLOYMENT_ENVIRONMENT_PROPERTY, deployment.strip());
        }
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, properties));
    }

    static Map<String, Object> resolve(String format, String level) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SERVICE_NAME_PROPERTY, "eegfaktura-billing");
        String f = normalize(format, "json");
        switch (f) {
            case "json" -> {
                properties.put("logging.structured.format.console", EcsJsonFormatter.class.getName());
                // the banner is plain text on stdout and would break "one JSON object per line"
                properties.put("spring.main.banner-mode", "off");
            }
            case "text" -> properties.put("logging.structured.format.console", EcsTextFormatter.class.getName());
            default -> throw new IllegalStateException("LOG_FORMAT must be json or text, not '" + format + "'");
        }
        String l = normalize(level, "info");
        if (l.equals("warning")) {
            l = "warn";
        }
        if (!LEVELS.contains(l)) {
            throw new IllegalStateException("LOG_LEVEL must be one of " + LEVELS + ", not '" + level + "'");
        }
        properties.put("logging.level.root", l);
        // the access line stays at every LOG_LEVEL (warn/error would drop it otherwise)
        properties.put("logging.level." + RequestLogFilter.ACCESS_LOGGER, "info");
        properties.put("logging.level." + StartupLog.LOGGER, "info");
        return properties;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip().toLowerCase(Locale.ROOT);
    }
}
