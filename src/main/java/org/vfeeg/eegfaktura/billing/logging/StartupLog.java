package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * One line when the service is ready, so the log shows since which build and at which level it runs. The level is the
 * root logger's own, not {@code LOG_LEVEL}: {@code LOGGING_LEVEL_ROOT} wins over it. Its logger stays at {@code info}
 * at every {@code LOG_LEVEL} ({@link LogSettings}), and the line names version and level only, no configuration.
 */
@Component
public class StartupLog {

    static final String LOGGER = "org.vfeeg.eegfaktura.billing.startup";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER);

    private final String version;

    public StartupLog(Environment environment) {
        this.version = environment.getProperty(LogSettings.SERVICE_VERSION_PROPERTY, "dev");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void started() {
        Level level = ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
                .getEffectiveLevel();
        LOG.atInfo().addKeyValue(LogFields.EVENT_ACTION, "service.start")
                .log("Service started: version {}, log level {}", version,
                        level.toString().toLowerCase(Locale.ROOT));
    }
}
