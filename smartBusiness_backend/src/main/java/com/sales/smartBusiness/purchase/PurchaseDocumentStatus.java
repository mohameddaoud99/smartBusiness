package com.sales.smartBusiness.purchase;

/**
 * Where a purchase document stands — the three statuses Finco shows for the supplier order and the
 * receipt, plus the two a purchase invoice moves through as it is paid.
 */
public enum PurchaseDocumentStatus {

    /** Being written: editable, no number yet, no effect on the stock. */
    DRAFT,

    /**
     * Validated: numbered and frozen. A validated receipt has put its goods into the stock. A validated invoice is
     * unpaid. A validated credit note has been taken off its invoice.
     */
    VALIDATED,

    /** Invoice with some of its total paid — set by the payments, never by hand. */
    PARTIALLY_PAID,

    /** Invoice paid in full — set by the payments, never by hand. */
    PAID,

    /** Cancelled after validation. A cancelled receipt or invoice has taken its goods back out. */
    CANCELLED
}
