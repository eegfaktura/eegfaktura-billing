package org.vfeeg.eegfaktura.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.vfeeg.eegfaktura.billing.config.JacksonConfig;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentType;
import org.vfeeg.eegfaktura.billing.domain.BillingRunStatus;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingParams;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON wire format of the REST API (consumed by eegfaktura-web), with the mapper Spring Boot builds
 * from the application properties and JacksonConfig. Pins what a Jackson or Spring Boot upgrade must
 * not change: field names and order, null fields, ISO dates, BigDecimal as a plain number, enums as
 * names, unknown request fields ignored.
 */
@SpringBootTest(classes = {JacksonAutoConfiguration.class, JacksonConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JsonWireFormatTests {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void billingRunDto() {
        BillingRunDTO run = new BillingRunDTO();
        run.setId(UUID.fromString("9f6bfef4-9217-4683-b3da-6648bf7533e2"));
        run.setClearingPeriodType("QUARTERLY");
        run.setClearingPeriodIdentifier("Abr_YQ-2026-3");
        run.setTenantId("RC100100");
        run.setRunStatus(BillingRunStatus.DONE);
        run.setRunStatusDateTime(LocalDateTime.of(2026, 10, 23, 4, 29, 57, 815309000));
        run.setNumberOfInvoices(5);

        assertEquals("{\"id\":\"9f6bfef4-9217-4683-b3da-6648bf7533e2\",\"clearingPeriodType\":\"QUARTERLY\","
                        + "\"clearingPeriodIdentifier\":\"Abr_YQ-2026-3\",\"tenantId\":\"RC100100\",\"runStatus\":\"DONE\","
                        + "\"runStatusDateTime\":\"2026-10-23T04:29:57.815309\",\"errorSummary\":null,\"mailStatus\":null,"
                        + "\"sendMailProtocol\":null,\"mailStatusDateTime\":null,\"sepaStatus\":null,"
                        + "\"sepaStatusDateTime\":null,\"numberOfInvoices\":5,\"numberOfCreditNotes\":null}",
                jsonMapper.writeValueAsString(run));
    }

    @Test
    void billingDocumentDto() {
        BillingDocumentDTO doc = new BillingDocumentDTO();
        doc.setDocumentNumber("RE-202600001");
        doc.setDocumentDate(LocalDate.of(2026, 10, 15));
        doc.setRecipientSepaMandateIssueDate(LocalDate.of(2025, 3, 1));
        doc.setIssuerBankIBAN("AT483200000012345864");
        doc.setVat1Percent(new BigDecimal("20.00"));
        doc.setVat1SumInEuro(new BigDecimal("3.92"));
        doc.setGrossAmountInEuro(new BigDecimal("23.50"));
        doc.setBillingDocumentType(BillingDocumentType.INVOICE);

        String json = jsonMapper.writeValueAsString(doc);
        assertTrue(json.startsWith("{\"id\":null,\"documentNumber\":\"RE-202600001\",\"documentDate\":\"2026-10-15\","
                + "\"status\":null,"), json);
        assertTrue(json.contains("\"recipientSepaMandateIssueDate\":\"2025-03-01\","), json);
        assertTrue(json.contains("\"issuerBankIBAN\":\"AT483200000012345864\","), json);
        assertTrue(json.contains("\"vat1Percent\":20.00,\"vat1SumInEuro\":3.92,"), json);
        assertTrue(json.contains("\"grossAmountInEuro\":23.50,"), json);
        assertTrue(json.endsWith("\"billingDocumentType\":\"INVOICE\",\"billingRunId\":null}"), json);
    }

    @Test
    void doBillingParamsAsSentByWeb() {
        DoBillingParams params = jsonMapper.readValue("{\"tenantId\":\"RC100100\",\"preview\":true,"
                + "\"clearingPeriodType\":\"QUARTERLY\",\"clearingDocumentDate\":\"2026-10-15\",\"unknownField\":1,"
                + "\"allocations\":[{\"participantId\":\"p1\",\"meteringPoint\":\"AT1\",\"allocationKWh\":124.69,"
                + "\"buckets\":[{\"key\":\"T1\",\"kWh\":1.5}]}]}", DoBillingParams.class);

        assertTrue(params.isPreview());
        assertEquals(LocalDate.of(2026, 10, 15), params.getClearingDocumentDate());
        assertEquals(new BigDecimal("124.69"), params.getAllocations()[0].getAllocationKWh());
        assertEquals(new BigDecimal("1.5"), params.getAllocations()[0].getBuckets()[0].getKWh());
    }
}
