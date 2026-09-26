package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every endpoint is refused to a caller who lacks its permission, and to anonymous
 * callers. The Angular guards are comfort — this is the barrier that actually holds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationTest extends IntegrationTest {

    private String adminToken;
    /** A SALES_MANAGER: full sales rights, no administration rights at all. */
    private String salesToken;
    /** A user with no role whatsoever — the secure default for a new account. */
    private String noRoleToken;

    @BeforeAll
    void setUpUsers() throws Exception {
        adminToken = registerCompany("Permission Test Co");

        String salesEmail = uniqueEmail("sales");
        createUser(adminToken, salesEmail, roleId(adminToken, "SALES_MANAGER"));
        salesToken = login(salesEmail, "Password123");

        String plainEmail = uniqueEmail("plain");
        createUser(adminToken, plainEmail);
        noRoleToken = login(plainEmail, "Password123");
    }

    // ----- Anonymous -----

    @Test
    @DisplayName("an anonymous call is refused with 401, in the standard error shape")
    void anonymousIsRefused() throws Exception {
        getJson("/api/users", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("a tampered token is refused")
    void tamperedTokenIsRefused() throws Exception {
        getJson("/api/users", "not.a.real.token")
                .andExpect(status().isUnauthorized());
    }

    // ----- A role that grants nothing administrative -----

    @Test
    @DisplayName("a sales manager cannot read the user list")
    void salesCannotListUsers() throws Exception {
        getJson("/api/users", salesToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("a sales manager cannot create a user")
    void salesCannotCreateUsers() throws Exception {
        postJson("/api/users", salesToken, """
                {"firstName":"New","lastName":"User","username":"n.user",
                 "email":"new@test.local","status":"ACTIVE","password":"Password123","roleIds":[]}
                """)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a sales manager cannot read or create roles")
    void salesCannotTouchRoles() throws Exception {
        getJson("/api/roles", salesToken).andExpect(status().isForbidden());
        postJson("/api/roles", salesToken, """
                {"label":"Mine","permissions":["SALE_VIEW"]}
                """).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a sales manager cannot read the audit log")
    void salesCannotReadAudit() throws Exception {
        getJson("/api/audit-logs", salesToken)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a sales manager cannot read or change company settings")
    void salesCannotTouchCompany() throws Exception {
        getJson("/api/company", salesToken).andExpect(status().isForbidden());
        // A valid payload, so this asserts the permission boundary and not field validation
        putJson("/api/company", salesToken, """
                {"name":"Renamed","currency":"TND"}
                """).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a sales manager cannot create a branch, and cannot even list them")
    void salesCannotTouchBranches() throws Exception {
        getJson("/api/branches", salesToken).andExpect(status().isForbidden());
        postJson("/api/branches", salesToken, """
                {"code":"NEW","name":"New"}
                """).andExpect(status().isForbidden());
    }

    // ----- No role at all -----

    @Test
    @DisplayName("a user with no role can sign in but reaches nothing")
    void noRoleReachesNothing() throws Exception {
        // The session endpoint works — they are authenticated, just not authorised
        getJson("/api/auth/me", noRoleToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(0));

        getJson("/api/users", noRoleToken).andExpect(status().isForbidden());
        getJson("/api/roles", noRoleToken).andExpect(status().isForbidden());
        getJson("/api/branches", noRoleToken).andExpect(status().isForbidden());
        getJson("/api/audit-logs", noRoleToken).andExpect(status().isForbidden());
        getJson("/api/company", noRoleToken).andExpect(status().isForbidden());
    }

    // ----- The administrator can do all of it -----

    @Test
    @DisplayName("the company administrator is allowed everywhere")
    void administratorIsAllowed() throws Exception {
        getJson("/api/users", adminToken).andExpect(status().isOk());
        getJson("/api/roles", adminToken).andExpect(status().isOk());
        getJson("/api/roles/permissions", adminToken).andExpect(status().isOk());
        getJson("/api/branches", adminToken).andExpect(status().isOk());
        getJson("/api/audit-logs", adminToken).andExpect(status().isOk());
        getJson("/api/company", adminToken).andExpect(status().isOk());
    }

    // ----- Revocation takes effect immediately -----

    @Test
    @DisplayName("deactivating a user invalidates their token on the very next call")
    void deactivationTakesEffectImmediately() throws Exception {
        String email = uniqueEmail("temp");
        long id = createUser(adminToken, email, roleId(adminToken, "VIEWER"));
        String token = login(email, "Password123");

        // The permissions are read from the database on every request, not from the token
        patchJson("/api/users/" + id + "/deactivate", adminToken)
                .andExpect(status().isOk());

        getJson("/api/auth/me", token)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("removing a role narrows access at once, without a new sign-in")
    void roleRemovalTakesEffectImmediately() throws Exception {
        String email = uniqueEmail("shrinking");
        long viewer = roleId(adminToken, "VIEWER");
        long id = createUser(adminToken, email, viewer);
        String token = login(email, "Password123");

        getJson("/api/auth/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(6));

        putJson("/api/users/" + id, adminToken, """
                {"firstName":"Test","lastName":"User","username":"%s",
                 "email":"%s","status":"ACTIVE","roleIds":[]}
                """.formatted(email.substring(0, email.indexOf('@')), email))
                .andExpect(status().isOk());

        getJson("/api/auth/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(0));
    }
}
