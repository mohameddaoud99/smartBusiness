package com.sales.smartBusiness.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * What the administrator fills in: a name and a set of ticked permissions.
 * No scope, no branch, no condition — that is the whole role editor.
 */
@Getter
@Setter
public class RoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 80, message = "Role name must not exceed 80 characters")
    private String label;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @NotEmpty(message = "Select at least one permission")
    private Set<Permission> permissions = new HashSet<>();
}
