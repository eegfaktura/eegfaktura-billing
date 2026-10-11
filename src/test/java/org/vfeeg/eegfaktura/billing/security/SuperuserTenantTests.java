package org.vfeeg.eegfaktura.billing.security;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Super-Admins (Realm-Rolle "superuser") duerfen jeden Mandanten anfragen; alle anderen
 * weiterhin nur die Mandanten aus ihrem Token.
 */
class SuperuserTenantTests {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void runFilter(final JwtAuthentication auth, final String tenantHeader) throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        when(tokenService.validateTokenAndGetAuthentication(anyString())).thenReturn(auth);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/billingConfigs");
        request.addHeader("Authorization", "Bearer x");
        request.addHeader("Tenant", tenantHeader);
        new JwtRequestFilter(tokenService).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    @DisplayName("Superuser ohne den Mandanten im Token kommt durch den Filter")
    void superuserPassesForeignTenant() {
        var auth = new JwtAuthentication("super06", List.of(new Authority("ROLE_EEG_ADMIN")), true);
        assertDoesNotThrow(() -> runFilter(auth, "RC100200"));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Normaler Admin wird fuer einen fremden Mandanten weiter abgewiesen")
    void adminRefusedForeignTenant() {
        var auth = new JwtAuthentication("admin", List.of(new Authority("RC100300"), new Authority("ROLE_EEG_ADMIN")));
        assertThrows(AccessDeniedException.class, () -> runFilter(auth, "RC100200"));
    }

    @Test
    @DisplayName("Normaler Admin kommt fuer den eigenen Mandanten durch")
    void adminPassesOwnTenant() {
        var auth = new JwtAuthentication("admin", List.of(new Authority("RC100200"), new Authority("ROLE_EEG_ADMIN")));
        assertDoesNotThrow(() -> runFilter(auth, "rc100200"));
    }

    @Test
    @DisplayName("realm_access.roles wird ausgewertet, fehlende Claims stoeren nicht")
    void realmRoleParsing() throws Exception {
        assertTrue(JwtTokenService.hasRealmRole(
                new JSONObject("{\"realm_access\":{\"roles\":[\"offline_access\",\"superuser\"]}}"), "superuser"));
        assertFalse(JwtTokenService.hasRealmRole(
                new JSONObject("{\"realm_access\":{\"roles\":[\"offline_access\"]}}"), "superuser"));
        assertFalse(JwtTokenService.hasRealmRole(new JSONObject("{}"), "superuser"));
        assertFalse(JwtTokenService.hasRealmRole(new JSONObject("{\"realm_access\":{}}"), "superuser"));
    }
}
