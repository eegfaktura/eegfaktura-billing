package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.FOREIGN;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/**
 * The tenant matrix of M2, applied to the rows a subclass names in {@link #endpoints()}. The subclass
 * is the {@code @WebMvcTest} of one resource; it stubs its mocked services so that every lookup
 * returns a record of a given community ({@link #stubRecordsOf}) or nothing ({@link #stubUnknownIds}).
 *
 * <p>Rows that fail on {@code master} are disabled with their {@code known-errors.md} number: a
 * refused foreign tenant or a missing header ends as 500, not 403 (#20); a header tenant that is not
 * in the token's {@code tenant} claim passes the filter (#1).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class EndpointMatrix extends WebSliceTest {

    @Autowired
    protected MockMvc mvc;

    /** The rows of the endpoint table this class covers. */
    protected abstract List<Endpoint> endpoints();

    /** Every lookup by id or tenant returns a record of {@code tenant}; every action succeeds. */
    protected abstract void stubRecordsOf(String tenant);

    /** Every lookup by id (and the update of the config) throws {@code NotFoundException}. */
    protected abstract void stubUnknownIds();

    /** The mocked services of the resource. */
    protected abstract Object[] services();

    /** The row with this method and full pattern. */
    protected Endpoint endpoint(HttpMethod method, String pattern) {
        return endpoints().stream().filter(e -> e.method() == method && e.pattern().equals(pattern))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(method + " " + pattern));
    }

    Stream<Endpoint> all() {
        return endpoints().stream();
    }

    Stream<Endpoint> idEndpoints() {
        return all().filter(e -> e.kind() == Endpoint.Kind.ID);
    }

    Stream<Endpoint> validatedEndpoints() {
        return all().filter(e -> e.invalidBody() != null);
    }

    @ParameterizedTest
    @MethodSource("all")
    void noTokenIsForbidden(Endpoint e) throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(e.request(OWN, OWN, null)).andExpect(status().isForbidden());
        assertNoServiceCalled();
    }

    @ParameterizedTest
    @MethodSource("all")
    void tokenWithoutAdminRoleIsForbidden(Endpoint e) throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(e.request(OWN, OWN, TestTokens.userWithoutAdminRole())).andExpect(status().isForbidden());
        assertNoServiceCalled();
    }

    @ParameterizedTest
    @MethodSource("all")
    void ownTenantGetsTheResult(Endpoint e) throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(e.request(OWN, OWN, TestTokens.admin())).andExpect(status().is(e.ownStatus()));
    }

    /** What holds today: the per-record comparison refuses, only the lookup ran, nothing else. */
    @ParameterizedTest
    @MethodSource("all")
    void foreignTenantIsRefusedBeforeAnyAction(Endpoint e) throws Exception {
        stubRecordsOf(FOREIGN);
        assertRefused(mvc.perform(e.request(FOREIGN, OWN, TestTokens.admin())).andReturn());
        assertOnlyLookupsCalled();
    }

    @Disabled("known-errors #20")
    @ParameterizedTest
    @MethodSource("all")
    void foreignTenantIsForbidden(Endpoint e) throws Exception {
        stubRecordsOf(FOREIGN);
        mvc.perform(e.request(FOREIGN, OWN, TestTokens.admin())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("all")
    void missingTenantHeaderIsRefusedBeforeAnyAction(Endpoint e) throws Exception {
        stubRecordsOf(OWN);
        assertRefused(mvc.perform(e.request(OWN, null, TestTokens.admin())).andReturn());
        assertOnlyLookupsCalled();
    }

    @Disabled("known-errors #20")
    @ParameterizedTest
    @MethodSource("all")
    void missingTenantHeaderIsForbidden(Endpoint e) throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(e.request(OWN, null, TestTokens.admin())).andExpect(status().isForbidden());
    }

    /** Header, path, body and stored record all name a community the token does not carry. */
    @Disabled("known-errors #1")
    @ParameterizedTest
    @MethodSource("all")
    void headerTenantNotInTokenClaimIsForbidden(Endpoint e) throws Exception {
        stubRecordsOf(FOREIGN);
        mvc.perform(e.request(FOREIGN, FOREIGN, TestTokens.admin())).andExpect(status().isForbidden());
        assertOnlyLookupsCalled();
    }

    @ParameterizedTest(allowZeroInvocations = true) // not every resource has such rows
    @MethodSource("idEndpoints")
    void unknownIdIsNotFound(Endpoint e) throws Exception {
        stubUnknownIds();
        mvc.perform(e.request(OWN, OWN, TestTokens.admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value(404))
                .andExpect(jsonPath("$.exception").value("NotFoundException"));
    }

    @ParameterizedTest(allowZeroInvocations = true) // not every resource has such rows
    @MethodSource("validatedEndpoints")
    void invalidBodyIsBadRequestWithFieldErrors(Endpoint e) throws Exception {
        stubRecordsOf(OWN);
        mvc.perform(e.invalidRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.exception").value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tenantId"))
                .andExpect(jsonPath("$.fieldErrors[0].errorCode").value("NotNull"));
        assertNoServiceCalled();
    }

    private static void assertRefused(MvcResult result) {
        int status = result.getResponse().getStatus();
        assertThat("refused, status " + status, status < 200 || status >= 300, is(true));
    }

    protected void assertNoServiceCalled() {
        assertThat(invocations().toList(), is(empty()));
    }

    /** Only {@code get(id)} may have run: no action, no listing, no data left the service. */
    protected void assertOnlyLookupsCalled() {
        assertThat(invocations().map(i -> i.getMethod().getName()).filter(n -> !n.equals("get")).toList(),
                is(empty()));
    }

    private Stream<Invocation> invocations() {
        return Arrays.stream(services()).flatMap(s -> Mockito.mockingDetails(s).getInvocations().stream());
    }
}
