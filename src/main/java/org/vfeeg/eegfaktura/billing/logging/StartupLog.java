package org.vfeeg.eegfaktura.billing.logging;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * One line when the service is ready, so the log shows since which build and at which level it runs: message
 * {@code service start}, {@code event.action=service.start}, {@code log.effective_level}; {@code service.version} is on
 * every line ({@link EcsJsonFormatter}). The level is the root logger's own, not {@code LOG_LEVEL}:
 * {@code LOGGING_LEVEL_ROOT} wins over it. Its logger stays at {@code info} at every {@code LOG_LEVEL}
 * ({@link LogSettings}), and the line names version and level only, no configuration.
 */
@Component
public class StartupLog {

    static final String LOGGER = "org.vfeeg.eegfaktura.billing.startup";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER);

    @EventListener(ApplicationReadyEvent.class)
    public void started() {
        Level level = ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
                .getEffectiveLevel();
        String effective = level.toString().toLowerCase(Locale.ROOT);
        LOG.atInfo().addKeyValue(LogFields.EVENT_ACTION, "service.start")
                .addKeyValue(LogFields.LOG_EFFECTIVE_LEVEL, effective)
                .log("service start");
    }
}
