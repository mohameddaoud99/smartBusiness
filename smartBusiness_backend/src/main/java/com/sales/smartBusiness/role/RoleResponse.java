package com.sales.smartBusiness.role;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
public class RoleResponse {

    private Long id;
    private String name;
    private String label;
    private String description;

    /** System roles are read-only: the frontend hides the edit and delete actions. */
    private boolean system;

    private Set<Permission> permissions;

    /** How many users hold this role — shown in the list and blocks deletion. */
    private long userCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
