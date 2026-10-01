package org.vfeeg.eegfaktura.billing.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container for the whole test JVM.
 *
 * <p>Started once in the static initializer and never stopped by a test class (Testcontainers' Ryuk
 * removes it when the JVM ends). {@code @Container} on a static field of a shared base class would
 * restart the container per subclass while the cached Spring context still points at the old port.
 * Usage in a test class: {@code @DynamicPropertySource static void db(DynamicPropertyRegistry r)
 * { PostgresContainerHolder.register(r); }}.
 */
public final class PostgresContainerHolder {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withUsername("sa")
                    .withPassword("sa");

    static {
        POSTGRES.start();
    }

    private PostgresContainerHolder() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
