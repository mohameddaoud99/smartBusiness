package com.sales.smartBusiness.branch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Status is not part of the payload: a branch is enabled or disabled through its
 * own endpoints, like a user account.
 */
@Getter
@Setter
public class BranchRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 20, message = "Code must not exceed 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "Code may only contain letters, digits, underscore and hyphen")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must not exceed 120 characters")
    private String name;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;
}
