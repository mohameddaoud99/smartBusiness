package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.numbering.DocumentType;
import lombok.Getter;

/**
 * What a sales document is. All types share one table and one engine. Each type names the numbering sequence it draws from.
 */
@Getter
public enum SalesDocumentType {

    QUOTE("Quote", DocumentType.QUOTE),
    SALES_ORDER("Sales order", DocumentType.SALES_ORDER),
    DELIVERY_NOTE("Delivery note", DocumentType.DELIVERY_NOTE),
    INVOICE("Invoice", DocumentType.SALES_INVOICE),
    CREDIT_NOTE("Credit note", DocumentType.SALES_CREDIT_NOTE),
    RETURN_NOTE("Return note", DocumentType.RETURN_NOTE);

    private final String label;
    private final DocumentType numbering;

    SalesDocumentType(String label, DocumentType numbering) {
        this.label = label;
        this.numbering = numbering;
    }

    /** The label with its article: "a quote", "an invoice" - for the messages the user reads. */
    public String withArticle() {
        return (this == INVOICE ? "an " : "a ") + label.toLowerCase();
    }

    /** A delivery note and an invoice say which warehouse their goods leave from, a return note where they come back to. */
    public boolean hasWarehouse() {
        return this == DELIVERY_NOTE || this == INVOICE || this == RETURN_NOTE;
    }
}
