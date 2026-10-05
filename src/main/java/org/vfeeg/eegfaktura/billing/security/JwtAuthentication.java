package org.vfeeg.eegfaktura.billing.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;

public class JwtAuthentication implements Authentication {

    private boolean isAuthenticated;
    private final String username;
    private final Collection<Authority> authorities = new ArrayList<>();
    private final boolean superuser;

    public JwtAuthentication(final String username,
                             final Collection<Authority> authorities) {
        this(username, authorities, false);
    }

    public JwtAuthentication(final String username,
                             final Collection<Authority> authorities,
                             final boolean superuser) {
        this.username = username;
        this.authorities.addAll(authorities);
        this.superuser = superuser;
        isAuthenticated = true;
    }

    /**
     * Realm-Rolle "superuser" (Betreiber/Support): darf jeden Mandanten anfragen, wie in
     * backend und energystore. Die Rolle EEG_ADMIN und die Pruefungen je Datensatz gelten
     * trotzdem.
     */
    public boolean isSuperuser() {
        return superuser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return username;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.isAuthenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return username;
    }

    /**
     * Traegt das Token den angefragten Mandanten? Die Rollen-Eintraege (ROLE_...) liegen
     * in derselben Liste, stoeren hier aber nicht: ein Mandant heisst nie so.
     */
    public boolean hasTenant(final String tenant) {
        if (tenant == null || tenant.isBlank()) {
            return false;
        }
        return authorities.stream()
                .map(Authority::getAuthority)
                .anyMatch(tenant::equalsIgnoreCase);
    }
}
