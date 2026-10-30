package org.vfeeg.eegfaktura.billing.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code LOG_FORMAT} and {@code LOG_LEVEL} onto Spring Boot's logging properties; unknown values stop the start. */
class LogSettingsTests {

    private static final String STRUCTURED = "logging.structured.format.console";

    @Test
    void defaultsAreJsonAndInfo() {
        Map<String, Object> p = LogSettings.resolve(null, null);
        assertEquals(EcsJsonFormatter.class.getName(), p.get(STRUCTURED));
        assertEquals("info", p.get("logging.level.root"));
        assertEquals(p, LogSettings.resolve(" ", ""));
    }

    @Test
    void textSwitchesStructuredLoggingOff() {
        assertFalse(LogSettings.resolve("TEXT", null).containsKey(STRUCTURED));
        assertEquals(EcsJsonFormatter.class.getName(), LogSettings.resolve(" Json ", null).get(STRUCTURED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "debug", "info", "warn", "error", "WARN"})
    void everyLevelIsAccepted(String level) {
        assertEquals(level.toLowerCase(), LogSettings.resolve(null, level).get("logging.level.root"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"verbose", "warning", "fatal", "off", "all"})
    void unknownLevelIsRefused(String level) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> LogSettings.resolve(null, level));
        assertEquals("LOG_LEVEL must be one of " + LogSettings.LEVELS + ", not '" + level + "'", e.getMessage());
    }

    @Test
    void unknownFormatIsRefused() {
        assertThrows(IllegalStateException.class, () -> LogSettings.resolve("ecs", null));
        assertThrows(IllegalStateException.class, () -> LogSettings.resolve("logfmt", null));
    }
}
