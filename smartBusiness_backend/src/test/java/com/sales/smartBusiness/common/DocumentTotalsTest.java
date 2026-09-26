package com.sales.smartBusiness.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one calculation both the sales and the purchase documents rely on — tested here on
 * plain numbers. The documents' own tests check the wiring; this one pins the arithmetic.
 */
class DocumentTotalsTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static Map<BigDecimal, BigDecimal> bases(String... rateAndBase) {
        Map<BigDecimal, BigDecimal> map = new TreeMap<>();
        for (int i = 0; i < rateAndBase.length; i += 2) {
            map.merge(bd(rateAndBase[i]), bd(rateAndBase[i + 1]), BigDecimal::add);
        }
        return map;
    }

    @Test
    @DisplayName("a line is quantity x price less its discount, rounded half-up to the millime")
    void lineTotal() {
        assertThat(DocumentTotals.lineTotal(bd("2"), bd("50.000"), bd("10"))).isEqualByComparingTo("90.000");
        // 3 x 0.0005 = 0.0015 -> 0.002, not 0.001
        assertThat(DocumentTotals.lineTotal(bd("3"), bd("0.0005"), bd("0"))).isEqualByComparingTo("0.002");
        assertThat(DocumentTotals.lineTotal(bd("5"), bd("100"), bd("100"))).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("the Finco case: 30.000, FODEC 1% in the VAT base, VAT 19%, stamp 1.000 = 37.057")
    void fincoCase() {
        DocumentTotals.Result result = DocumentTotals.compute(bases("19", "30.000"),
                List.of(new DocumentTotals.Surcharge(bd("1"), true)), List.of(bd("1.000")));

        assertThat(result.subtotal()).isEqualByComparingTo("30.000");
        assertThat(result.surchargeAmounts().get(0)).isEqualByComparingTo("0.300");
        assertThat(result.vatRows()).hasSize(1);
        assertThat(result.vatRows().get(0).base()).isEqualByComparingTo("30.300");
        assertThat(result.vatRows().get(0).amount()).isEqualByComparingTo("5.757");
        assertThat(result.total()).isEqualByComparingTo("37.057");
    }

    @Test
    @DisplayName("a surcharge outside the VAT base is added to the total but not taxed")
    void surchargeOutsideVatBase() {
        DocumentTotals.Result result = DocumentTotals.compute(bases("19", "100.000"),
                List.of(new DocumentTotals.Surcharge(bd("2"), false)), List.of());

        assertThat(result.surchargeAmounts().get(0)).isEqualByComparingTo("2.000");
        assertThat(result.vatRows().get(0).amount()).isEqualByComparingTo("19.000");
        assertThat(result.total()).isEqualByComparingTo("121.000");
    }

    @Test
    @DisplayName("each VAT rate is a row of its own, sorted by rate, and a surcharge is spread over them")
    void severalRates() {
        DocumentTotals.Result result = DocumentTotals.compute(bases("19", "100.000", "7", "100.000"),
                List.of(new DocumentTotals.Surcharge(bd("1"), true)), List.of());

        assertThat(result.vatRows()).extracting(row -> row.rate().intValue()).containsExactly(7, 19);
        assertThat(result.surchargeAmounts().get(0)).isEqualByComparingTo("2.000");
        assertThat(result.vatRows().get(0).amount()).isEqualByComparingTo("7.070");
        assertThat(result.vatRows().get(1).amount()).isEqualByComparingTo("19.190");
        assertThat(result.total()).isEqualByComparingTo("228.260");
    }

    @Test
    @DisplayName("no surcharge and no flat charge: total is the base plus VAT")
    void plain() {
        DocumentTotals.Result result = DocumentTotals.compute(bases("19", "200.000"), List.of(), List.of());

        assertThat(result.surchargeAmounts()).isEmpty();
        assertThat(result.total()).isEqualByComparingTo("238.000");
    }

    @Test
    @DisplayName("several flat charges are all added after the VAT")
    void flatCharges() {
        DocumentTotals.Result result = DocumentTotals.compute(bases("0", "10.000"),
                List.of(), List.of(bd("1.000"), bd("0.500")));

        assertThat(result.total()).isEqualByComparingTo("11.500");
    }
}
