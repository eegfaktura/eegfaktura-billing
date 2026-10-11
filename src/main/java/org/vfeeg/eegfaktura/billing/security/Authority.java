package org.vfeeg.eegfaktura.billing.security;

import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.GrantedAuthority;

import java.util.Locale;

public class Authority implements GrantedAuthority {

    private final String tenant;

    public Authority(@NotNull String tenant) {
        this.tenant = tenant;
    }
    @Override
    public String getAuthority() {
        return tenant;
    }

    /**
     * Ohne equals/hashCode vergleicht jede Collection-Suche die Objektidentitaet und
     * findet nie etwas. Genau daran ist die Mandantenpruefung in JwtRequestFilter
     * jahrelang stumm vorbeigelaufen. Gross-/Kleinschreibung wird ignoriert, wie
     * ueberall sonst beim Mandantenvergleich.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Authority)) {
            return false;
        }
        String otherTenant = ((Authority) other).tenant;
        return tenant == null ? otherTenant == null : tenant.equalsIgnoreCase(otherTenant);
    }

    @Override
    public int hashCode() {
        return tenant == null ? 0 : tenant.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return tenant;
    }
}
