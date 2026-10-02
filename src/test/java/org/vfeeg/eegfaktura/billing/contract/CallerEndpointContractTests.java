package org.vfeeg.eegfaktura.billing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.vfeeg.eegfaktura.billing.rest.Endpoint;
import org.vfeeg.eegfaktura.billing.rest.EndpointTable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Every request a known caller makes ({@code contracts/web/endpoints.json}, {@code
 * contracts/v3/endpoints.json}) reaches a billing endpoint (M6). The endpoints come from the M2 table,
 * which {@code EndpointMappingGuardTests} ties to the live mappings in both directions — so a renamed
 * or removed path that a caller still uses fails here with the caller's source line. Bodies and
 * multipart parts are checked by {@link CallerRequestContractTests}, responses by {@link
 * CallerResponseContractTests}.
 */
class CallerEndpointContractTests {

    private static final PathPatternParser PARSER = new PathPatternParser();

    static List<ContractFixtures.Call> calls() {
        return ContractFixtures.calls();
    }

    /** The endpoint Spring would route the call to: same method, most specific matching pattern. */
    static Optional<Endpoint> route(String method, String path, List<Endpoint> table) {
        PathContainer container = PathContainer.parsePath(path);
        return table.stream()
                .filter(e -> e.method().name().equals(method))
                .filter(e -> PARSER.parse(e.pattern()).matches(container))
                .min((a, b) -> PathPattern.SPECIFICITY_COMPARATOR.compare(PARSER.parse(a.pattern()),
                        PARSER.parse(b.pattern())));
    }

    @ParameterizedTest
    @MethodSource("calls")
    void everyCallerRequestReachesABillingEndpoint(ContractFixtures.Call call) {
        assertThat("no billing endpoint for " + call, route(call.method(), call.path(), EndpointTable.ALL).isPresent(),
                is(true));
    }

    @ParameterizedTest
    @MethodSource("calls")
    void bodyAndMultipartReferencesAreConsistent(ContractFixtures.Call call) {
        if (call.body() != null) {
            assertThat("missing body fixture of " + call, ContractFixtures.exists(call.body()), is(true));
            JsonNode fixture = ContractFixtures.read(call.body());
            assertThat(call.toString(), fixture.get("method").asText() + " " + fixture.get("path").asText(),
                    is(call.method() + " " + call.path()));
            assertThat(call.toString(), fixture.get("source").get("commit"), notNullValue());
        }
        if (call.multipart() != null) {
            assertThat("billing reads the part 'file' (@RequestParam): " + call, call.multipart(), is("file"));
        }
    }

    /** The routes used by the two callers, as listed in {@code AGENT_LOG.md} (M6): 18 web, 20 v3, 21 distinct. */
    @Test
    void theCallersUseTwentyOneOfTheThirtyOneEndpoints() {
        Set<String> web = routes("eegfaktura-web");
        Set<String> v3 = routes("eegfaktura-v3");
        Set<String> all = new TreeSet<>(web);
        all.addAll(v3);
        assertThat(web, hasSize(18));
        assertThat(v3, hasSize(20));
        assertThat(all, hasSize(21));
    }

    @Test
    void aPathBillingDoesNotServeIsReported() {
        assertThat(route("GET", "/api/billingRuns/00000000-0000-0000-0000-00000000a001/documents",
                EndpointTable.ALL).isPresent(), is(false));
        assertThat(route("POST", "/api/billingConfigs/00000000-0000-0000-0000-00000000a001",
                EndpointTable.ALL).isPresent(), is(false));
    }

    private static Set<String> routes(String caller) {
        return calls().stream().filter(c -> c.caller().equals(caller))
                .map(c -> route(c.method(), c.path(), EndpointTable.ALL).map(Endpoint::key).orElse("none: " + c))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
