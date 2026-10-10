package org.vfeeg.eegfaktura.billing.rest;

import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.function.Function;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.Kind.BODY_TENANT;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.Kind.ID;
import static org.vfeeg.eegfaktura.billing.rest.Endpoint.Kind.PATH_TENANT;

/**
 * The 31 mapped handlers under {@code /api/**} (counted 2026-10-01; commented-out mappings do not
 * count, {@code known-errors.md} #29). {@code EndpointMappingGuardTests} compares this table with
 * the live mappings in both directions: a new endpoint without a row fails it.
 */
public final class EndpointTable {

    static final Function<String, String> CONFIG_BODY =
            tenant -> "{\"tenantId\":\"" + tenant + "\",\"documentNumberSequenceLength\":5}";
    static final String CONFIG_WITHOUT_TENANT = "{\"documentNumberSequenceLength\":5}";
    static final Function<String, String> BILLING_BODY = tenant -> "{\"tenantId\":\"" + tenant + "\","
            + "\"clearingPeriodType\":\"YQ\",\"clearingPeriodIdentifier\":\"YQ-2024-1\","
            + "\"clearingDocumentDate\":\"2024-04-02\",\"allocations\":[],\"preview\":true}";

    private static final String RUNS = "/api/billingRuns";
    private static final String CONFIGS = "/api/billingConfigs";

    public static final List<Endpoint> ALL = List.of(
            // BillingRunResource (8)
            row(BillingRunResource.class, GET, RUNS + "/{tenantId}/{clearingPeriodType}/{clearingPeriodIdentifier}",
                    PATH_TENANT, 200),
            row(BillingRunResource.class, GET, RUNS + "/{id}", ID, 200),
            row(BillingRunResource.class, GET, RUNS + "/{id}/participantAmounts", ID, 200),
            row(BillingRunResource.class, GET, RUNS + "/{id}/billingDocuments", ID, 200),
            row(BillingRunResource.class, GET, RUNS + "/{id}/billingDocuments/xlsx", ID, 200),
            row(BillingRunResource.class, GET, RUNS + "/{id}/billingDocuments/archive", ID, 200),
            row(BillingRunResource.class, GET, RUNS + "/{id}/billingDocuments/sendmail", ID, 200),
            row(BillingRunResource.class, DELETE, RUNS + "/{id}", ID, 204),
            // BillingConfigResource (11): CRUD 5, images 6
            row(BillingConfigResource.class, GET, CONFIGS + "/tenant/{tenantId}", PATH_TENANT, 200),
            row(BillingConfigResource.class, GET, CONFIGS + "/{id}", ID, 200),
            new Endpoint(BillingConfigResource.class, POST, CONFIGS, BODY_TENANT, 201,
                    CONFIG_BODY, false, CONFIG_WITHOUT_TENANT),
            new Endpoint(BillingConfigResource.class, PUT, CONFIGS + "/{id}", ID, 200,
                    CONFIG_BODY, false, CONFIG_WITHOUT_TENANT),
            row(BillingConfigResource.class, DELETE, CONFIGS + "/{id}", ID, 204),
            image(POST, CONFIGS + "/{id}/logoImage", true),
            image(POST, CONFIGS + "/{id}/footerImage", true),
            image(DELETE, CONFIGS + "/{id}/logoImage", false),
            image(DELETE, CONFIGS + "/{id}/footerImage", false),
            image(GET, CONFIGS + "/{id}/logoImage", false),
            // demands a multipart part although it is a GET (known-errors #21); the matrix sends one
            image(GET, CONFIGS + "/{id}/footerImage", true),
            // BillingDocumentResource (3)
            row(BillingDocumentResource.class, GET, "/api/billingDocuments/tenant/{id}/{year}", PATH_TENANT, 200),
            row(BillingDocumentResource.class, GET, "/api/billingDocuments/{id}", ID, 200),
            row(BillingDocumentResource.class, DELETE, "/api/billingDocuments/{id}", ID, 204),
            // BillingDocumentFileResource (3)
            row(BillingDocumentFileResource.class, GET, "/api/billingDocumentFiles/{id}", ID, 200),
            row(BillingDocumentFileResource.class, GET, "/api/billingDocumentFiles/tenant/{id}", PATH_TENANT, 200),
            row(BillingDocumentFileResource.class, DELETE, "/api/billingDocumentFiles/{id}", ID, 204),
            // BillingDocumentItemResource (2)
            row(BillingDocumentItemResource.class, GET, "/api/billingDocumentItems/{id}", ID, 200),
            row(BillingDocumentItemResource.class, DELETE, "/api/billingDocumentItems/{id}", ID, 204),
            // BillingDocumentNumberResource (2)
            row(BillingDocumentNumberResource.class, GET, "/api/billingDocumentNumbers/{id}", ID, 200),
            row(BillingDocumentNumberResource.class, DELETE, "/api/billingDocumentNumbers/{id}", ID, 204),
            // BillingResource (1): annotated 201, returns 200; validates nothing (known-errors #31)
            new Endpoint(BillingResource.class, POST, "/api/billing", BODY_TENANT, 200,
                    BILLING_BODY, false, null),
            // FileDataResource (1)
            row(FileDataResource.class, GET, "/api/fileData/{id}", ID, 200)
    );

    private EndpointTable() {
    }

    public static List<Endpoint> of(Class<?> resource) {
        return ALL.stream().filter(e -> e.resource() == resource).toList();
    }

    private static Endpoint row(Class<?> resource, HttpMethod method, String pattern, Endpoint.Kind kind,
                                int ownStatus) {
        return new Endpoint(resource, method, pattern, kind, ownStatus, null, false, null);
    }

    private static Endpoint image(HttpMethod method, String pattern, boolean multipart) {
        return new Endpoint(BillingConfigResource.class, method, pattern, ID, 200, null, multipart, null);
    }
}
