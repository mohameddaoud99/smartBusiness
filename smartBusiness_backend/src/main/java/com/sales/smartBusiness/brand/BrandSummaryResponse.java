package com.sales.smartBusiness.brand;

import lombok.Getter;
import lombok.Setter;

/** The little of a brand that other modules need to show: a product's manufacturer. */
@Getter
@Setter
public class BrandSummaryResponse {

    private Long id;
    private String name;
}
