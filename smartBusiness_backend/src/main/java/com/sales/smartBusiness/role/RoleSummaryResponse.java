package com.sales.smartBusiness.role;

import lombok.Getter;
import lombok.Setter;

/** The little of a role that other modules need to show: user lists, session payload. */
@Getter
@Setter
public class RoleSummaryResponse {

    private Long id;
    private String name;
    private String label;
    private boolean system;
}
