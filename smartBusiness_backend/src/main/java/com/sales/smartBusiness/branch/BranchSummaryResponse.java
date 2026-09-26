package com.sales.smartBusiness.branch;

import lombok.Getter;
import lombok.Setter;

/** The little of a branch that other modules need to show: user lists, user detail. */
@Getter
@Setter
public class BranchSummaryResponse {

    private Long id;
    private String code;
    private String name;
}
