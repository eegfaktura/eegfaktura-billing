package org.vfeeg.eegfaktura.billing.rest;

import jakarta.validation.Valid;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.service.FileDataService;
import org.vfeeg.eegfaktura.billing.util.NotFoundException;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.vfeeg.eegfaktura.billing.rest.TestTokens.OWN;

/**
 * The {@code ErrorResponse} format of {@code RestExceptionHandler}: 404, 400 with field errors,
 * {@code ResponseStatusException} and an unexpected exception (no detail leaks). A probe controller,
 * nested here so that no other slice scans it, throws each exception.
 */
@WebMvcTest(controllers = FileDataResource.class)
@Import(RestExceptionHandlerWebTests.ProbeController.class)
class RestExceptionHandlerWebTests extends WebSliceTest {

    @RestController
    public static class ProbeController {

        @GetMapping("/probe/notFound")
        public String notFound() {
            throw new NotFoundException("probe record missing");
        }

        @PostMapping("/probe/valid")
        public String valid(@RequestBody @Valid BillingConfigDTO body) {
            return "ok";
        }

        @GetMapping("/probe/status")
        public String status() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "probe conflict");
        }

        @GetMapping("/probe/unexpected")
        public String unexpected() {
            throw new IllegalStateException("internal detail that must not leak");
        }
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    FileDataService fileDataService;

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header("Tenant", OWN).header(HttpHeaders.AUTHORIZATION, TestTokens.admin());
    }

    @Test
    void notFoundIs404WithMessage() throws Exception {
        mvc.perform(authorized(get("/probe/notFound")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value(404))
                .andExpect(jsonPath("$.exception").value("NotFoundException"))
                .andExpect(jsonPath("$.message").value("probe record missing"))
                .andExpect(jsonPath("$.fieldErrors").value(nullValue()));
    }

    @Test
    void invalidBodyIs400WithOneFieldErrorPerViolation() throws Exception {
        mvc.perform(authorized(post("/probe/valid").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.exception").value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.message").value(nullValue()))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tenantId"))
                .andExpect(jsonPath("$.fieldErrors[0].errorCode").value("NotNull"));
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() throws Exception {
        mvc.perform(authorized(get("/probe/status")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value(409))
                .andExpect(jsonPath("$.exception").value("ResponseStatusException"))
                .andExpect(jsonPath("$.message").value("409 CONFLICT \"probe conflict\""));
    }

    @Test
    void unexpectedExceptionIs500WithoutDetail() throws Exception {
        mvc.perform(authorized(get("/probe/unexpected")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.httpStatus").value(500))
                .andExpect(jsonPath("$.exception").value("IllegalStateException"))
                .andExpect(jsonPath("$.message").value(nullValue()));
    }

    /** A malformed id is the caller's error; the catch-all handler turns it into 500 today. */
    @Disabled("known-errors #31")
    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(authorized(get("/api/fileData/not-a-uuid")))
                .andExpect(status().isBadRequest());
    }
}
