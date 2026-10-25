package org.vfeeg.eegfaktura.billing.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;
import java.util.function.Function;

/**
 * One row of the endpoint table: a mapped handler under {@code /api/**} and what the tenant matrix
 * needs to call it.
 *
 * @param resource     the controller class
 * @param method       HTTP method
 * @param pattern      the path pattern exactly as Spring maps it (compared by the guard test)
 * @param kind         where the tenant comes from
 * @param ownStatus    the status the code returns for a caller of the own community
 * @param body         JSON body for a given tenant, or {@code null}
 * @param multipart    the handler demands a multipart part {@code file}
 * @param invalidBody  a body that fails bean validation, or {@code null} if the handler validates nothing
 */
public record Endpoint(Class<?> resource, HttpMethod method, String pattern, Kind kind, int ownStatus,
                       Function<String, String> body, boolean multipart, String invalidBody) {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    /** Where the community of a request comes from. */
    public enum Kind {
        /** the handler loads a record by id and compares its stored tenant */
        ID,
        /** the tenant is a path variable */
        PATH_TENANT,
        /** the tenant is a field of the JSON body */
        BODY_TENANT
    }

    public String key() {
        return method.name() + " " + pattern;
    }

    @Override
    public String toString() {
        return key();
    }

    /** The concrete path; tenant variables get {@code tenant}, the id variable gets {@link #ID}. */
    public String path(String tenant) {
        String path = pattern
                .replace("{tenantId}", tenant)
                .replace("{clearingPeriodType}", "YQ")
                .replace("{clearingPeriodIdentifier}", "2024-Q1")
                .replace("{year}", "2024");
        return path.replace("{id}", kind == Kind.PATH_TENANT ? tenant : ID.toString());
    }

    /**
     * Builds the request.
     *
     * @param tenant        tenant in path or body
     * @param headerTenant  value of the {@code Tenant} header, {@code null} = no header
     * @param authorization value of the {@code Authorization} header, {@code null} = no token
     */
    public MockHttpServletRequestBuilder request(String tenant, String headerTenant, String authorization) {
        MockHttpServletRequestBuilder builder = multipart
                ? MockMvcRequestBuilders.multipart(method, path(tenant))
                        .file(new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, PNG))
                : MockMvcRequestBuilders.request(method, path(tenant));
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body.apply(tenant));
        }
        if (headerTenant != null) {
            builder.header("Tenant", headerTenant);
        }
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return builder;
    }

    /** Request with an own-community admin token and header, carrying {@link #invalidBody}. */
    public MockHttpServletRequestBuilder invalidRequest() {
        return MockMvcRequestBuilders.request(method, path(TestTokens.OWN))
                .contentType(MediaType.APPLICATION_JSON).content(invalidBody)
                .header("Tenant", TestTokens.OWN)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.admin());
    }
}
