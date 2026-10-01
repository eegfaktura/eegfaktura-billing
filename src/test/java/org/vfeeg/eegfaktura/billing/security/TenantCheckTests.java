package org.vfeeg.eegfaktura.billing.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die Mandantentrennung ab, die jahrelang wirkungslos war: die Pruefung in
 * JwtRequestFilter war invertiert UND konnte wegen des fehlenden equals() in Authority
 * ohnehin nie etwas finden.
 */
class TenantCheckTests {

    @AfterEach
    void clearTenant() {
        TenantContext.setCurrentTenant(null);
    }

    @Test
    @DisplayName("Authority vergleicht den Mandanten, nicht die Objektidentitaet")
    void authorityEquality() {
        assertTrue(new Authority("RC100200").equals(new Authority("RC100200")));
        assertTrue(new Authority("RC100200").equals(new Authority("rc100200")));
        assertFalse(new Authority("RC100200").equals(new Authority("RC999999")));

        // Genau hier lag der Fehler: ohne equals() findet contains() nie etwas.
        assertTrue(List.of(new Authority("RC100200")).contains(new Authority("RC100200")));
    }

    @Test
    @DisplayName("hasTenant findet nur Mandanten aus dem Token")
    void hasTenant() {
        var auth = new JwtAuthentication("tester",
                List.of(new Authority("RC100200"), new Authority("RC100300"), new Authority("ROLE_EEG_ADMIN")));

        assertTrue(auth.hasTenant("RC100200"));
        assertTrue(auth.hasTenant("rc100300"));

        assertFalse(auth.hasTenant("RC999999"));
        assertFalse(auth.hasTenant(null));
        assertFalse(auth.hasTenant(" "));
    }

    @Test
    @DisplayName("validateTenant laesst nur den Mandanten der Anfrage durch")
    void validateTenant() {
        TenantContext.setCurrentTenant(new Authority("RC100200"));

        assertDoesNotThrow(() -> TenantContext.validateTenant("RC100200"));
        assertDoesNotThrow(() -> TenantContext.validateTenant("rc100200"));

        assertThrows(AccessDeniedException.class, () -> TenantContext.validateTenant("RC999999"));
        assertThrows(AccessDeniedException.class, () -> TenantContext.validateTenant(null));
    }

    @Test
    @DisplayName("Ohne Mandanten in der Anfrage wird abgewiesen statt zu knallen")
    void validateTenantWithoutRequestTenant() {
        TenantContext.setCurrentTenant(null);
        assertThrows(AccessDeniedException.class, () -> TenantContext.validateTenant("RC100200"));

        // Fehlender Header: Authority traegt null. Frueher lief das in eine
        // NullPointerException und damit in einen 500er statt in eine Abweisung.
        TenantContext.setCurrentTenant(new Authority(null));
        assertThrows(AccessDeniedException.class, () -> TenantContext.validateTenant("RC100200"));
    }

    @Test
    @DisplayName("Ein Mandant aus einer fremden Gemeinschaft ist nicht gleich")
    void foreignTenantIsNotEqual() {
        assertNotEquals(new Authority("RC100200"), new Authority("RC100300"));
    }
}
