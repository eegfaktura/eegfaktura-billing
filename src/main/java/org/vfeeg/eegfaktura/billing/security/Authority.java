package org.vfeeg.eegfaktura.billing.security;

import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;

@EqualsAndHashCode
public class Authority implements GrantedAuthority {

    private final String tenant;

    public Authority(@NotNull String tenant) {
        this.tenant = tenant;
    }

    @Override
    public String getAuthority() {
        return tenant;
    }
}
