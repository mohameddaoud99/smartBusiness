package com.sales.smartBusiness.user;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class UserRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 60, message = "First name must not exceed 60 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 60, message = "Last name must not exceed 60 characters")
    private String lastName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
            message = "Username may only contain letters, digits, dot, underscore and hyphen")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    @NotNull(message = "Status is required")
    private UserStatus status;

    /**
     * Which site this user is based at. Optional and purely organisational — it
     * never restricts what the user may do; permissions stay company-wide.
     */
    private Long branchId;

    /**
     * May be left empty: a user with no role can sign in but reaches nothing.
     * That is the secure default for a freshly created account.
     */
    private Set<Long> roleIds = new HashSet<>();

    /**
     * Required on creation, ignored on update.
     * Use the reset-password endpoint to change it.
     */
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;
}
