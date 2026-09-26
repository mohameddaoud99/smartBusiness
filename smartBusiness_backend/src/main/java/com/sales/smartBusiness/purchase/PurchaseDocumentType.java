package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.numbering.DocumentType;
import lombok.Getter;

/**
 * What a purchase document is. All share one table and one engine (the mirror of the sales
 * documents).
 */
@Getter
public enum PurchaseDocumentType {

    PURCHASE_ORDER("Purchase order", DocumentType.PURCHASE_ORDER),
    GOODS_RECEIPT("Goods receipt", DocumentType.GOODS_RECEIPT),
    PURCHASE_INVOICE("Purchase invoice", DocumentType.PURCHASE_INVOICE),
    PURCHASE_CREDIT_NOTE("Supplier credit note", DocumentType.PURCHASE_CREDIT_NOTE),
    PURCHASE_RETURN_NOTE("Supplier return note", DocumentType.PURCHASE_RETURN_NOTE);

    private final String label;
    private final DocumentType numbering;

    PurchaseDocumentType(String label, DocumentType numbering) {
        this.label = label;
        this.numbering = numbering;
    }

    /** A goods receipt and a purchase invoice say which warehouse their goods go into, a return note where they leave from. */
    public boolean hasWarehouse() {
        return this == GOODS_RECEIPT || this == PURCHASE_INVOICE || this == PURCHASE_RETURN_NOTE;
    }
}
