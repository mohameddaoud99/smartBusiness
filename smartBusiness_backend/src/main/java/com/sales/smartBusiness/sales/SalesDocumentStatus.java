package com.sales.smartBusiness.sales;

/**
 * Where a document stands. Not every status exists for every type — see
 * {@link SalesDocument#canMoveTo}.
 */
public enum SalesDocumentStatus {

    /** Being written: editable, no number yet. */
    DRAFT,

    /** Issued: numbered and frozen. A delivery note is "created" here — its goods have not left yet. */
    ISSUED,

    /** Quote the customer agreed to. */
    ACCEPTED,

    /** Quote the customer turned down. */
    REJECTED,

    /** Sales order the company has committed to fulfil. */
    CONFIRMED,

    /** Delivery note whose goods have left: the stock has been taken out. */
    DELIVERED,

    /** Invoice with some of its total paid — set by the payments, never by hand. An invoice with none is simply ISSUED. */
    PARTIALLY_PAID,

    /** Invoice paid in full — set by the payments, never by hand. */
    PAID,

    /** Order, delivery note or invoice that will not go ahead (what it took out of the stock has been put back). */
    CANCELLED
}
