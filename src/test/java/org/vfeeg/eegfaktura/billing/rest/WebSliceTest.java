package org.vfeeg.eegfaktura.billing.rest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.vfeeg.eegfaktura.billing.repos.InMemoryLockRepository;
import org.vfeeg.eegfaktura.billing.security.JwtSecurityConfig;
import org.vfeeg.eegfaktura.billing.security.JwtTokenService;
import org.vfeeg.eegfaktura.billing.util.AppProperties;

/**
 * Base of the {@code @WebMvcTest} slices (M2): annotations only. Each subclass names its controller
 * with {@code @WebMvcTest(controllers = …)} and mocks the services with {@code @MockitoBean}.
 *
 * <p>Imported for real: the security chain of production ({@code JwtSecurityConfig}, profile
 * {@code !dev}; never activate {@code dev}), {@code JwtTokenService} with the test-only certificate,
 * {@code AppProperties}, and the concrete {@code InMemoryLockRepository} (the resources synchronize
 * on its lock objects). {@code JwtRequestFilter}, {@code TenantFilter} and {@code RestExceptionHandler}
 * are picked up by the slice and run in their real order (security chain, then {@code TenantFilter}).
 */
@Import({JwtSecurityConfig.class, JwtTokenService.class, InMemoryLockRepository.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.jwt-public-key-file=" + TestTokens.CERT_FILE)
public abstract class WebSliceTest {
}
