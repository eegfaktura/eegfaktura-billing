package org.vfeeg.eegfaktura.billing.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentArchiveService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentFileService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentItemService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentMailService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentNumberService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentXlsxService;
import org.vfeeg.eegfaktura.billing.service.BillingRunService;
import org.vfeeg.eegfaktura.billing.service.BillingService;
import org.vfeeg.eegfaktura.billing.service.FileDataService;
import org.vfeeg.eegfaktura.billing.service.ParticipantAmountService;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Guard: the endpoint table of the tenant matrix equals the live {@code /api/**} mappings, in both
 * directions. A new endpoint without a row (or a row without an endpoint) fails here. All
 * controllers are loaded; all services are mocks.
 */
@WebMvcTest
class EndpointMappingGuardTests extends WebSliceTest {

    @MockitoBean BillingConfigService billingConfigService;
    @MockitoBean BillingDocumentArchiveService billingDocumentArchiveService;
    @MockitoBean BillingDocumentFileService billingDocumentFileService;
    @MockitoBean BillingDocumentItemService billingDocumentItemService;
    @MockitoBean BillingDocumentMailService billingDocumentMailService;
    @MockitoBean BillingDocumentNumberService billingDocumentNumberService;
    @MockitoBean BillingDocumentService billingDocumentService;
    @MockitoBean BillingDocumentXlsxService billingDocumentXlsxService;
    @MockitoBean BillingRunService billingRunService;
    @MockitoBean BillingService billingService;
    @MockitoBean FileDataService fileDataService;
    @MockitoBean ParticipantAmountService participantAmountService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private Set<String> liveApiMappings() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> {
                    Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
                    Collection<String> methods = info.getMethodsCondition().getMethods().stream()
                            .map(Enum::name).toList();
                    return patterns.stream().flatMap(p -> (methods.isEmpty() ? List.of("ANY") : methods)
                            .stream().map(m -> m + " " + p));
                })
                .filter(key -> key.substring(key.indexOf(' ') + 1).startsWith("/api/"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> keys(List<Endpoint> table) {
        return table.stream().map(Endpoint::key).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Keys present on one side only, marked "no row:" or "no endpoint:". */
    static Set<String> difference(Set<String> live, Set<String> table) {
        Set<String> diff = new TreeSet<>();
        live.stream().filter(k -> !table.contains(k)).forEach(k -> diff.add("no row: " + k));
        table.stream().filter(k -> !live.contains(k)).forEach(k -> diff.add("no endpoint: " + k));
        return diff;
    }

    @Test
    void everyApiHandlerHasExactlyOneRow() {
        assertThat(EndpointTable.ALL, hasSize(31));
        assertThat(keys(EndpointTable.ALL), hasSize(31));
        assertThat(difference(liveApiMappings(), keys(EndpointTable.ALL)), is(empty()));
        assertThat(liveApiMappings(), hasSize(31));
    }

    @Test
    void aMissingRowIsReported() {
        Endpoint removed = EndpointTable.ALL.get(0);
        List<Endpoint> shortened = EndpointTable.ALL.stream().filter(e -> e != removed).toList();
        assertThat(difference(liveApiMappings(), keys(shortened)), contains("no row: " + removed.key()));
    }
}
