package com.sales.smartBusiness.tax;

import lombok.Getter;
import lombok.Setter;

/** The little of a tax that other modules need to show: a product's default taxes. */
@Getter
@Setter
public class TaxSummaryResponse {

    private Long id;
    private String name;
}
