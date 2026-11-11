package org.vfeeg.eegfaktura.billing.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPRINGDOC_ENABLED=true: API docs and Swagger UI are served (to a valid EEG admin token). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "SPRINGDOC_ENABLED=true")
class SpringdocEnabledTests extends SpringdocAccessTestBase {

    @Test
    void apiDocsServed() throws Exception {
        HttpResponse<String> response = getWithToken("/v3/api-docs");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"openapi\""), response.body());
    }

    @Test
    void swaggerUiServed() throws Exception {
        HttpResponse<String> response = getWithToken("/swagger-ui/index.html");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().toLowerCase().contains("swagger"), response.body());
    }
}
