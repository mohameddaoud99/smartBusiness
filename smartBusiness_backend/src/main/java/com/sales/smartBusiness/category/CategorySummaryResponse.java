package com.sales.smartBusiness.category;

import lombok.Getter;
import lombok.Setter;

/** The little of a category that other modules need to show: a product's family. */
@Getter
@Setter
public class CategorySummaryResponse {

    private Long id;
    private String name;
}
