package org.vfeeg.eegfaktura.billing.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Default configuration (no SPRINGDOC_ENABLED): no API docs and no Swagger UI, even for an EEG admin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringdocDisabledTests extends SpringdocAccessTestBase {

    @Test
    void apiDocsNotServed() throws Exception {
        HttpResponse<String> response = getWithToken("/v3/api-docs");
        assertNotEquals(200, response.statusCode());
        assertFalse(response.body().contains("\"openapi\""), response.body());
    }

    @Test
    void swaggerUiNotServed() throws Exception {
        HttpResponse<String> response = getWithToken("/swagger-ui/index.html");
        assertNotEquals(200, response.statusCode());
        assertFalse(response.body().toLowerCase().contains("swagger"), response.body());
    }

    @Test
    void apiStillServed() throws Exception {
        // control: the token is accepted, so the refusals above are not an authentication failure;
        // the community has no billing config yet, which the API itself answers with 404
        HttpResponse<String> response = getWithToken("/api/billingConfigs/tenant/RC100100");
        assertEquals(404, response.statusCode());
    }
}
