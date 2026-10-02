package org.vfeeg.eegfaktura.billing.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * The contract database of M6: its own PostgreSQL container with the <b>real legacy view</b>
 * {@code base.billing_masterdata}, built from the SQL files that {@code src/test/resources/legacy-base/}
 * copies from eegfaktura-v3 (see the README there). Never the M3 database of {@link
 * PostgresContainerHolder}, which holds a plain <i>table</i> of the same name.
 *
 * <p>Started once per JVM in the static initializer (same pattern as {@code PostgresContainerHolder});
 * the files run in {@link #SCRIPTS} order, {@code up} scripts only.
 */
public final class LegacyBaseDatabase {

    /** The minimal ordered subset of v3's {@code docker/legacy-base/01..08} that builds the view. */
    public static final List<String> SCRIPTS = List.of(
            "legacy-base/01_20250603103206_activeMeterView.up.sql",
            "legacy-base/02_20250603150318_create_views.up.sql",
            "legacy-base/04_20250604182344_add_bank_fields.up.sql",
            "legacy-base/05_20250605091807_add_account_info_to_billing_view.up.sql");

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("eegfaktura")
                    .withUsername("legacy")
                    .withPassword("legacy");

    static {
        POSTGRES.start();
        try (Connection connection = connection()) {
            for (String script : SCRIPTS) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(script));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot build the legacy base schema", e);
        }
    }

    private LegacyBaseDatabase() {
    }

    /** A new connection (auto-commit on); the caller closes it. */
    public static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
