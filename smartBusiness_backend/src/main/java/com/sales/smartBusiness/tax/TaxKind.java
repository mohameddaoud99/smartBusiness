package com.sales.smartBusiness.tax;

/**
 * How a tax attaches to a document. The order matters: the taxes screen and the
 * future calculation engine both walk the values in this order.
 */
public enum TaxKind {

    /** A VAT rate applied to each line's taxable amount. */
    VAT_RATE,

    /** A percentage tax on the pre-VAT amount (e.g. FODEC) — may or may not enlarge the VAT base. */
    PERCENTAGE_SURCHARGE,

    /** A flat amount added once per document (e.g. stamp duty). */
    FIXED_PER_DOCUMENT
}
