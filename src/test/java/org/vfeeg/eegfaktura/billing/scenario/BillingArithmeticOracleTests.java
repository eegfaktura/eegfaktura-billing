package org.vfeeg.eegfaktura.billing.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.model.BillingDocumentDTO;
import org.vfeeg.eegfaktura.billing.model.DoBillingResults;
import org.vfeeg.eegfaktura.billing.support.BillingMasterdataBuilder;
import org.vfeeg.eegfaktura.billing.support.BillingScenarioBase;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Optional M3 item (B-16): billing's item amounts against an oracle not written by billing's authors.
 *
 * <p>{@code v3oracle/billing-arithmetic-cases.json} is a byte-identical copy of
 * {@code eegfaktura-v3/backend/src/test/resources/golden/billing-arithmetic-cases.json} (AGPL-3.0, same
 * organisation; repository commit {@code 0b785d2}, file last changed in {@code f6540a0}; sha256
 * {@code 830d846c…}; identical with the copy in {@code energy-mock}). Refresh by copying the file again and
 * reviewing the diff. Only the rows billing has a line for are used: 1 (consumer energy), 2 (producer
 * energy), 3 (participant fee), 4 (meter-point fee). Row 5 (base fee) has no line in billing
 * ({@code tariff_basic_fee} is read but never billed, {@code open-points.md} B-24); row 14 (instalment) is
 * not a billing concept. A disagreement is a question for the business side (B-13), not automatically a
 * billing defect.
 */
class BillingArithmeticOracleTests extends BillingScenarioBase {

    static Stream<Arguments> cases() throws IOException {
        try (InputStream in = new ClassPathResource("v3oracle/billing-arithmetic-cases.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            return StreamSupport.stream(root.get("cases").spliterator(), false)
                    .filter(c -> c.get("row").asInt() <= 4)
                    .map(c -> Arguments.of(c.get("name").asText(), c))
                    .toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void itemAmountsEqualTheV3Oracle(String name, JsonNode c) {
        int row = c.get("row").asInt();
        boolean producer = row == 2 || (row == 4 && c.get("producer").asBoolean());
        BillingMasterdataBuilder master = producer ? producer(1, "M1") : consumer(1, "M1");
        String kwh = "0";
        switch (row) {
            case 1, 2 -> {
                kwh = text(c, "kwh");
                master = (producer ? master.creditAmount(text(c, "centPerKwh")) : master.workingFee(text(c, "centPerKwh")))
                        .freeKwh(text(c, "freeKwh"))
                        .column("tariff_discount", decimal(c, "discount"))
                        .column("tariff_use_vat", c.get("useVat").asBoolean())
                        .column("tariff_vat_in_percent", decimal(c, "vat"));
            }
            case 3 -> master = master.column("tariff_participant_fee", decimal(c, "price"))
                    .column("tariff_participant_fee_discount", decimal(c, "discount"))
                    .column("tariff_participant_fee_use_vat", c.get("useVat").asBoolean())
                    .column("tariff_participant_fee_vat_in_percent", decimal(c, "vat"));
            case 4 -> master = master.meteringPointFee(text(c, "price"), "ZP", text(c, "meteringPointVat"))
                    .column("tariff_use_vat", c.get("useVat").asBoolean())
                    .column("tariff_vat_in_percent", decimal(c, "vat"));
            default -> throw new IllegalArgumentException("row " + row);
        }
        DoBillingResults results = bill(insert(world().master(master, kwh), false), true);

        assertThat(results.getAbstractText(), is(RESULT_PREVIEW_OK));
        List<BillingDocumentItem> lines = itemsOfRun(results, switch (row) {
            case 1, 2 -> i -> i.getMeteringPointType() != null;
            case 3 -> i -> i.getText().equals("Mitgliedsgebühr");
            default -> i -> i.getText().startsWith("Zählpunktgebühr");
        });
        if (c.has("line") && !c.get("line").asBoolean()) {
            assertThat(name + ": no line", lines, is(empty()));
            return;
        }
        assertThat(name, lines, hasSize(1));
        BillingDocumentItem line = lines.get(0);
        // the item's VAT rate is not compared: billing stores the tariff rate even with VAT off, v3 stores 0
        assertThat(name + " amount", line.getAmount(),
                comparesEqualTo(c.has("amount") ? decimal(c, "amount") : BigDecimal.ONE));
        assertThat(name + " net", line.getNetValue(), comparesEqualTo(decimal(c, "net")));
        assertThat(name + " vat", line.getVatValueInEuro(), comparesEqualTo(decimal(c, "vatValue")));
        assertThat(name + " gross", line.getGrossValue(), comparesEqualTo(decimal(c, "gross")));
    }

    private List<BillingDocumentItem> itemsOfRun(DoBillingResults results, Predicate<BillingDocumentItem> kind) {
        List<BillingDocumentItem> lines = new ArrayList<>();
        for (BillingDocumentDTO doc : documents(results.getBillingRunId())) {
            items(doc.getId()).stream().filter(kind).forEach(lines::add);
        }
        return lines;
    }

    private static String text(JsonNode c, String field) {
        return c.get(field).asText();
    }

    private static BigDecimal decimal(JsonNode c, String field) {
        return new BigDecimal(c.get(field).asText());
    }
}
