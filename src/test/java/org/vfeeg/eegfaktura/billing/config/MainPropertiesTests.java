package org.vfeeg.eegfaktura.billing.config;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * src/test/resources/application.properties replaces the file of production on the test classpath, so the tests of the
 * wire format and of springdoc would stay green without these settings in the file that ships.
 */
class MainPropertiesTests {

    @Test
    void theFileThatShipsHoldsTheSettingsTheTestsRelyOn() throws Exception {
        Properties main = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            main.load(reader);
        }

        assertThat(main.getProperty("spring.jackson.use-jackson2-defaults"), is("true"));
        assertThat(main.getProperty("springdoc.api-docs.enabled"), is("${SPRINGDOC_ENABLED:false}"));
        assertThat(main.getProperty("springdoc.swagger-ui.enabled"), is("${SPRINGDOC_ENABLED:false}"));
    }
}
