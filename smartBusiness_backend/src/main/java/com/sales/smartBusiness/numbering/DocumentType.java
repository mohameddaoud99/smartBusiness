package com.sales.smartBusiness.numbering;

import lombok.Getter;

/**
 * The records the application numbers — commercial documents, plus the reference codes
 * of master data. Extend as modules land: a company that has no sequence for a type yet
 * gets one with these defaults on first use, so a new value needs no data migration.
 */
@Getter
public enum DocumentType {

    QUOTE("Quote", "QUO", 5, true),
    SALES_ORDER("Sales order", "SO", 5, true),
    DELIVERY_NOTE("Delivery note", "BL", 5, true),
    SALES_INVOICE("Invoice", "INV", 5, true),
    SALES_CREDIT_NOTE("Credit note", "CN", 5, true),
    RETURN_NOTE("Return note", "RN", 5, true),
    PURCHASE_ORDER("Purchase order", "PO", 5, true),
    PURCHASE_INVOICE("Purchase invoice", "PINV", 5, true),
    PURCHASE_CREDIT_NOTE("Supplier credit note", "PCN", 5, true),
    PURCHASE_RETURN_NOTE("Supplier return note", "PRN", 5, true),
    GOODS_RECEIPT("Goods receipt", "GR", 5, true),
    /** A customer's reference code, e.g. {@code C-0001} — no year, it is not a document. */
    CUSTOMER("Customer", "C", 4, false),
    /** A supplier's reference code, e.g. {@code F-0001} — no year, it is not a document. */
    SUPPLIER("Supplier", "F", 4, false),
    /** A product's reference code (SKU), e.g. {@code P-0001} — no year, it is not a document. */
    PRODUCT("Product", "P", 4, false);

    private final String label;
    private final String defaultPrefix;
    private final int defaultPadding;
    private final boolean defaultIncludeYear;

    DocumentType(String label, String defaultPrefix, int defaultPadding, boolean defaultIncludeYear) {
        this.label = label;
        this.defaultPrefix = defaultPrefix;
        this.defaultPadding = defaultPadding;
        this.defaultIncludeYear = defaultIncludeYear;
    }
}
