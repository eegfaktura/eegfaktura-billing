package org.vfeeg.eegfaktura.billing.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;

public class JwtAuthentication implements Authentication {

    private boolean isAuthenticated;
    private final String username;
    private final Collection<Authority> authorities = new ArrayList<>();

    public JwtAuthentication(final String username,
                             final Collection<Authority> authorities) {
        this.username = username;
        this.authorities.addAll(authorities);
        isAuthenticated = true;
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
}
