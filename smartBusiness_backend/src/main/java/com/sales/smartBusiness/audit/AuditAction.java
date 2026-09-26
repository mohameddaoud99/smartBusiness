package com.sales.smartBusiness.audit;

import lombok.Getter;

/**
 * The security-relevant events a company administrator needs to be able to review.
 * <p>
 * Business operations (a sale, a stock move) do not belong here — they carry their own
 * history inside their module.
 */
@Getter
public enum AuditAction {

    LOGIN_SUCCESS("Signed in"),
    LOGIN_FAILED("Sign-in refused"),

    USER_CREATED("User created"),
    USER_UPDATED("User updated"),
    USER_ROLES_CHANGED("User roles changed"),
    USER_ACTIVATED("User activated"),
    USER_DISABLED("User deactivated"),
    USER_PASSWORD_RESET("Password reset"),
    PASSWORD_CHANGED("Password changed"),

    ROLE_CREATED("Role created"),
    ROLE_UPDATED("Role updated"),
    ROLE_DELETED("Role deleted"),

    BRANCH_CREATED("Branch created"),
    BRANCH_UPDATED("Branch updated"),
    BRANCH_ACTIVATED("Branch activated"),
    BRANCH_DISABLED("Branch deactivated"),

    COMPANY_UPDATED("Company settings updated"),
    COMPANY_MODULES_CHANGED("Modules changed by platform admin"),

    BANK_ACCOUNT_CREATED("Bank account added"),
    BANK_ACCOUNT_UPDATED("Bank account updated"),
    BANK_ACCOUNT_DELETED("Bank account removed"),

    TAX_CREATED("Tax created"),
    TAX_UPDATED("Tax updated"),
    TAX_DELETED("Tax deleted"),

    NUMBERING_UPDATED("Document numbering updated");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }
}
