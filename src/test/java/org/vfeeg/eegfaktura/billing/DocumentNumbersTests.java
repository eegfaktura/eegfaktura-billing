package org.vfeeg.eegfaktura.billing;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentNumber;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentItemRepository;
import org.vfeeg.eegfaktura.billing.repos.BillingDocumentNumberGenerator;
import org.vfeeg.eegfaktura.billing.repos.BillingMasterdataRepository;
import org.vfeeg.eegfaktura.billing.repos.FileDataRepository;
import org.vfeeg.eegfaktura.billing.service.BillingConfigService;
import org.vfeeg.eegfaktura.billing.service.BillingDocumentService;
import org.vfeeg.eegfaktura.billing.service.BillingRunService;
import org.vfeeg.eegfaktura.billing.service.BillingService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@Testcontainers
@Transactional
class DocumentNumbersTests {

    @Autowired
    BillingConfigService billingConfigService;

    @Autowired
    BillingRunService billingRunService;

    @Autowired
    BillingService billingService;

    @Autowired
    BillingMasterdataRepository billingMasterdataRepository;

    @Autowired
    TestAppProperties testAppProperties;

    @Autowired
    FileDataRepository fileDataRepository;

    @Autowired
    BillingDocumentService billingDocumentService;

    @Autowired
    BillingDocumentNumberGenerator billingDocumentNumberGenerator;

    @Autowired
    BillingDocumentItemRepository billingDocumentItemRepository;

    @Container
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withUsername("sa")
            .withPassword("sa")
            .withReuse(true);

    @Test
    void contextLoads() {
    }

    @Test
    void testContainer() {
       assertThat(postgreSQLContainer.isRunning(), is(true));
    }

    @Test
    void testBillingDocumentNumberGenerator_BeginWithZero() {

        BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                "ABC", 2023, null, 0L, 5);
        assertThat(billingDocumentNumber.getDocumentNumber(), is("202300000"));
    }

    @Test
    void testBillingDocumentNumberGenerator_BeginWithOne() {
        BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                "ABC", 2023, null, 1L, 5);
        assertThat(billingDocumentNumber.getDocumentNumber(), is("202300001"));
    }

    @Test
    void testBillingDocumentNumberGenerator_ExceedingSequenceLength() {
        BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                "ABC", 2023, null, 999999L, 5);
        assertThat(billingDocumentNumber.getDocumentNumber(), is("2023999999"));
    }

    @Test
    void testBillingDocumentNumberGenerator_TenInARow() {
        for (int i = 42; i < 52; i++) {
            BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                    "XYZ", 2023, "T", 42L, 5);
            assertThat(billingDocumentNumber.getDocumentNumber(), is("T2023000" + i));
        }
    }

    @Test
    void testBillingDocumentNumberGenerator_YearMix() {
        for (int i = 42; i < 52; i++) {
            BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                    "XYZ", 2023, "T", 42L, 5);
            assertThat(billingDocumentNumber.getDocumentNumber(), is("T2023000" + i));
        }
        for (int i = 42; i < 52; i++) {
            BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                    "XYZ", 2022, "T", 42L, 5);
            assertThat(billingDocumentNumber.getDocumentNumber(), is("T2022000" + i));
        }
        for (int i = 52; i < 62; i++) {
            BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                    "XYZ", 2023, "T", 42L, 5);
            assertThat(billingDocumentNumber.getDocumentNumber(), is("T2023000" + i));
        }
        for (int i = 52; i < 62; i++) {
            BillingDocumentNumber billingDocumentNumber = billingDocumentNumberGenerator.getNext(
                    "XYZ", 2022, "T", 42L, 5);
            assertThat(billingDocumentNumber.getDocumentNumber(), is("T2022000" + i));
        }
    }

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    }

}
