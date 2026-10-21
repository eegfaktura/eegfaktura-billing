package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.domain.BillingDocumentItem;
import org.vfeeg.eegfaktura.billing.domain.MeteringPointType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Pilot feedback 20.10.2026: a block with a single position repeated it as "Zwischensumme Zaehlpunkt".
class BillingPdfRowsTests {

    private static BillingDocumentItem energy(String meteringPointId, String text, String kWh) {
        BillingDocumentItem item = new BillingDocumentItem();
        item.setMeteringPointId(meteringPointId);
        item.setMeteringPointType(MeteringPointType.CONSUMER);
        item.setText(text);
        item.setAmount(new BigDecimal(kWh));
        item.setNetValue(BigDecimal.ONE);
        item.setGrossValue(BigDecimal.ONE);
        item.setVatValueInEuro(BigDecimal.ZERO);
        return item;
    }

    private static List<String> rowTypes(List<Map<String, ?>> rows) {
        return rows.stream().map(r -> String.valueOf(r.get("rowType"))).toList();
    }

    @Test
    void singlePositionBlockHasNoSubtotal() {
        BillingPdfService service = new BillingPdfService(null, null);
        List<Map<String, ?>> rows = service.buildGroupedItemRows(
                List.of(energy("AT001", "Tarif: Bezug", "114.05")), Map.of());
        assertEquals(List.of("H", "I"), rowTypes(rows));
    }

    @Test
    void severalPositionsKeepTheSubtotal() {
        BillingPdfService service = new BillingPdfService(null, null);
        List<Map<String, ?>> rows = service.buildGroupedItemRows(
                List.of(energy("AT001", "Tarif: Basis", "100"), energy("AT001", "Tarif: Tag (06:00 - 08:00)", "14.05"),
                        energy("AT002", "Tarif: Bezug", "50")), Map.of());
        assertEquals(List.of("H", "I", "I", "S", "H", "I"), rowTypes(rows));
    }
}
