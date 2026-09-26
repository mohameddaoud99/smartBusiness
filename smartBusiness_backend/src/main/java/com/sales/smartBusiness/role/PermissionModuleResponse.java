package com.sales.smartBusiness.role;

import java.util.List;

/**
 * The permission catalogue, grouped by module — one row of the permission matrix.
 * <p>
 * Built from the enum, so a new business module appears in the administration screen
 * as soon as its permissions are declared, with no frontend change.
 */
public record PermissionModuleResponse(String module,
                                       String label,
                                       List<PermissionResponse> permissions) {

    public record PermissionResponse(String name, String label) {
    }
}
