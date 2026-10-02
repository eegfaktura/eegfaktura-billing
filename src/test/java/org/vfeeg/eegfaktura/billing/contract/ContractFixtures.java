package org.vfeeg.eegfaktura.billing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.vfeeg.eegfaktura.billing.rest.TestTokens;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the caller fixtures of {@code src/test/resources/contracts/} (M6; format and refresh steps in
 * the README there). Plain Jackson, no Spring: the fixtures are data the callers send.
 */
final class ContractFixtures {

    static final String ROOT = "contracts/";
    static final List<String> CALLER_INVENTORIES = List.of("web/endpoints.json", "v3/endpoints.json");

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One request a caller makes, as listed in its {@code endpoints.json}. */
    record Call(String caller, String method, String path, String source, String body, String multipart) {
        @Override
        public String toString() {
            return caller + " " + method + " " + path + " (" + source + ")";
        }
    }

    private ContractFixtures() {
    }

    static JsonNode read(String file) {
        try (InputStream in = ContractFixtures.class.getClassLoader().getResourceAsStream(ROOT + file)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture " + ROOT + file);
            }
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean exists(String file) {
        return ContractFixtures.class.getClassLoader().getResource(ROOT + file) != null;
    }

    static List<Call> calls() {
        List<Call> calls = new ArrayList<>();
        for (String inventory : CALLER_INVENTORIES) {
            JsonNode root = read(inventory);
            for (JsonNode call : root.get("calls")) {
                calls.add(new Call(root.get("caller").asText(), call.get("method").asText(), call.get("path").asText(),
                        call.get("source").asText(), text(call, "body"), text(call, "multipart")));
            }
        }
        return calls;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * The request of a body fixture exactly as the caller sends it (method, path, headers, JSON body),
     * plus the bearer token of an own-community admin; {@code body} may be patched by the test first.
     */
    static MockHttpServletRequestBuilder request(JsonNode fixture, JsonNode body) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .request(org.springframework.http.HttpMethod.valueOf(fixture.get("method").asText()),
                        fixture.get("path").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin());
        fixture.get("headers").fields().forEachRemaining(h -> builder.header(h.getKey(), h.getValue().asText()));
        return builder;
    }

    static MockHttpServletRequestBuilder request(JsonNode fixture) {
        return request(fixture, fixture.get("body"));
    }
}
