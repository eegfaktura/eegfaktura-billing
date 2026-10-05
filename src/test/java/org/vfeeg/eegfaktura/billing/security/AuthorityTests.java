package org.vfeeg.eegfaktura.billing.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * {@code Authority} equality, the reason the tenant check never matched before upstream #50:
 * case-insensitive on the tenant, consistent with {@code hashCode}, so set and list lookups find it.
 * The basic cases are in {@code TenantCheckTests}; these cover the remaining branches.
 */
class AuthorityTests {

    @Test
    void anAuthorityEqualsItself() {
        Authority authority = new Authority("RC100001");
        assertThat(authority.equals(authority), is(true));
    }

    @Test
    void anAuthorityIsNotEqualToAnotherType() {
        assertThat(new Authority("RC100001").equals("RC100001"), is(false));
        assertThat(new Authority("RC100001").equals(null), is(false));
    }

    @Test
    void nullTenantsAreEqualOnlyToEachOther() {
        assertThat(new Authority(null).equals(new Authority(null)), is(true));
        assertThat(new Authority(null).equals(new Authority("RC100001")), is(false));
        assertThat(new Authority("RC100001").equals(new Authority(null)), is(false));
    }

    @Test
    void hashCodeFollowsTheCaseInsensitiveEquality() {
        assertThat(new Authority("RC100001").hashCode(), is(new Authority("rc100001").hashCode()));
        assertThat(new Authority(null).hashCode(), is(0));
        assertThat(Set.of(new Authority("RC100001")).contains(new Authority("rc100001")), is(true));
        assertThat(new Authority("RC100001").hashCode(), is(not(new Authority("RC100002").hashCode())));
    }

    @Test
    void toStringIsTheTenant() {
        assertThat(new Authority("RC100001").toString(), is("RC100001"));
    }
}
