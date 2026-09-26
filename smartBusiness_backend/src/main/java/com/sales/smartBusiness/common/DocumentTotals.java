package com.sales.smartBusiness.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The calculation chain of every commercial document — sales and purchases alike — written
 * ONCE, in the order the Finco analysis observed: line discount, then surcharges (FODEC),
 * then VAT on the base plus the surcharges that enter it, then flat charges (stamp duty).
 * Everything is rounded half-up to 3 decimals (the millime).
 * <p>
 * Pure numbers in, numbers out — no entity, no Spring — so it is testable on its own. The
 * documents feed it their lines and taxes and write the result back.
 */
public final class DocumentTotals {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private DocumentTotals() {
    }

    /** Quantity × price after the line discount, before any tax. */
    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate) {
        return quantity.multiply(unitPrice)
                .multiply(HUNDRED.subtract(discountRate))
                .divide(HUNDRED, 3, RoundingMode.HALF_UP);
    }

    /** A percentage tax on the document — {@code includedInVatBase} says whether it enlarges the VAT base. */
    public record Surcharge(BigDecimal rate, boolean includedInVatBase) {
    }

    /** One VAT rate of the document: what it applied to, and what it came to. */
    public record VatRow(BigDecimal rate, BigDecimal base, BigDecimal amount) {
    }

    /**
     * @param surchargeAmounts one amount per surcharge, in the order they were given
     * @param vatRows          one row per VAT rate, sorted by rate
     */
    public record Result(BigDecimal subtotal, List<BigDecimal> surchargeAmounts,
                         List<VatRow> vatRows, BigDecimal total) {
    }

    /**
     * @param baseByRate  the sum of the line totals for each VAT rate
     * @param surcharges  the percentage taxes chosen for the whole document
     * @param flatAmounts the flat charges chosen for the whole document
     */
    public static Result compute(Map<BigDecimal, BigDecimal> baseByRate,
                                 List<Surcharge> surcharges,
                                 List<BigDecimal> flatAmounts) {
        // Sorted by rate (compareTo, so 19 and 19.000 are the same rate): a stable recap order
        Map<BigDecimal, BigDecimal> sorted = new TreeMap<>(baseByRate);

        BigDecimal subtotal = sorted.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal;

        // A surcharge is computed per VAT rate, so the amount shown is exactly what entered
        // each VAT base and the recap always adds up.
        List<BigDecimal> surchargeAmounts = new ArrayList<>();
        for (Surcharge surcharge : surcharges) {
            BigDecimal amount = BigDecimal.ZERO;
            for (BigDecimal base : sorted.values()) {
                amount = amount.add(percentOf(base, surcharge.rate()));
            }
            surchargeAmounts.add(amount);
            total = total.add(amount);
        }

        List<VatRow> vatRows = new ArrayList<>();
        for (Map.Entry<BigDecimal, BigDecimal> group : sorted.entrySet()) {
            BigDecimal vatBase = group.getValue();
            for (Surcharge surcharge : surcharges) {
                if (surcharge.includedInVatBase()) {
                    vatBase = vatBase.add(percentOf(group.getValue(), surcharge.rate()));
                }
            }
            BigDecimal vat = percentOf(vatBase, group.getKey());
            vatRows.add(new VatRow(group.getKey(), vatBase, vat));
            total = total.add(vat);
        }

        for (BigDecimal flat : flatAmounts) {
            total = total.add(flat);
        }
        return new Result(subtotal, surchargeAmounts, vatRows, total);
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(HUNDRED, 3, RoundingMode.HALF_UP);
    }
}
