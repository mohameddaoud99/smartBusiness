package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.tax.TaxKind;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseDocumentTaxResponse {

    /** The company tax it came from; null for a VAT row. */
    private Long taxId;
    private TaxKind kind;
    private String name;
    private BigDecimal rate;
    private BigDecimal base;
    private BigDecimal amount;
}
