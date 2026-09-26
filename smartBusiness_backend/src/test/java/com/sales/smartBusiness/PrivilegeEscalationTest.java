package com.sales.smartBusiness;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance criterion #15: nobody grants themselves more than they already hold,
 * and a company cannot lock itself out of its own administration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrivilegeEscalationTest extends IntegrationTest {

    private String adminToken;
    private String adminEmail;
    private long adminId;

    /** Allowed to manage roles and users, but holds no stock or company permission. */
    private String assistantToken;
    private long assistantId;
    private long assistantRoleId;

    @BeforeAll
    void setUp() throws Exception {
        adminEmail = uniqueEmail("owner");
        adminToken = registerCompany("Escalation Test Co", adminEmail);
        adminId = number(getJson("/api/auth/me", adminToken)
                .andReturn().getResponse().getContentAsString(), "$.id");

        String role = postJson("/api/roles", adminToken, """
                {"label":"Assistant Admin",
                 "permissions":["ROLE_VIEW","ROLE_CREATE","ROLE_UPDATE","USER_VIEW","USER_CREATE","USER_UPDATE"]}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assistantRoleId = number(role, "$.id");

        String assistantEmail = uniqueEmail("assistant");
        assistantId = createUser(adminToken, assistantEmail, assistantRoleId);
        assistantToken = login(assistantEmail, "Password123");
    }

    // ----- Nobody widens their own access -----

    @Test
    @DisplayName("a user cannot promote themselves to administrator")
    void cannotPromoteSelfToAdministrator() throws Exception {
        long adminRole = roleId(assistantToken, "COMPANY_ADMIN");

        // Two guards would refuse this; the permission-subset one is reached first
        putJson("/api/users/" + assistantId, assistantToken, """
                {"firstName":"Test","lastName":"User","username":"assistant-self",
                 "email":"%s","status":"ACTIVE","roleIds":[%d]}
                """.formatted(uniqueEmail("assistant-self"), adminRole))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("You cannot grant permissions that you do not have yourself"));
    }

    @Test
    @DisplayName("a user cannot change their own roles, even to ones within their reach")
    void cannotChangeOwnRolesAtAll() throws Exception {
        // A role built strictly from permissions the assistant already holds, so the
        // subset guard passes and the own-roles guard is the one that fires
        String harmless = postJson("/api/roles", assistantToken, """
                {"label":"Role Reader","permissions":["ROLE_VIEW"]}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        putJson("/api/users/" + assistantId, assistantToken, """
                {"firstName":"Test","lastName":"User","username":"assistant-self",
                 "email":"%s","status":"ACTIVE","roleIds":[%d]}
                """.formatted(uniqueEmail("assistant-self"), number(harmless, "$.id")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You cannot change your own roles"));
    }

    @Test
    @DisplayName("a user cannot deactivate their own account")
    void cannotDeactivateSelf() throws Exception {
        patchJson("/api/users/" + assistantId + "/deactivate", assistantToken)
                .andExpect(status().isForbidden()); // no USER_DISABLE

        patchJson("/api/users/" + adminId + "/deactivate", adminToken)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You cannot deactivate your own account"));
    }

    @Test
    @DisplayName("a role cannot be built around a permission its author does not hold")
    void cannotCreateRoleBeyondOwnPermissions() throws Exception {
        postJson("/api/roles", assistantToken, """
                {"label":"Stock Master","permissions":["STOCK_ADJUST"]}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("You cannot grant permissions that you do not have yourself"));
    }

    @Test
    @DisplayName("an existing role cannot be widened beyond its editor's own permissions")
    void cannotWidenRoleBeyondOwnPermissions() throws Exception {
        putJson("/api/roles/" + assistantRoleId, assistantToken, """
                {"label":"Assistant Admin",
                 "permissions":["ROLE_VIEW","ROLE_CREATE","ROLE_UPDATE","USER_VIEW","COMPANY_UPDATE"]}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("You cannot grant permissions that you do not have yourself"));
    }

    @Test
    @DisplayName("escalation by proxy is refused too — creating a more powerful colleague")
    void cannotCreateAMorePowerfulUser() throws Exception {
        long adminRole = roleId(assistantToken, "COMPANY_ADMIN");

        postJson("/api/users", assistantToken, """
                {"firstName":"Puppet","lastName":"Admin","username":"puppet",
                 "email":"%s","status":"ACTIVE","password":"Password123","roleIds":[%d]}
                """.formatted(uniqueEmail("puppet"), adminRole))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("You cannot grant permissions that you do not have yourself"));
    }

    // ----- Standard roles are untouchable -----

    @Test
    @DisplayName("a standard role cannot be edited, even by the administrator")
    void systemRoleCannotBeEdited() throws Exception {
        long viewer = roleId(adminToken, "VIEWER");

        putJson("/api/roles/" + viewer, adminToken, """
                {"label":"Viewer plus","permissions":["USER_VIEW","USER_CREATE"]}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Standard roles cannot be modified"));
    }

    @Test
    @DisplayName("a standard role cannot be deleted, even by the administrator")
    void systemRoleCannotBeDeleted() throws Exception {
        long viewer = roleId(adminToken, "VIEWER");

        deleteJson("/api/roles/" + viewer, adminToken)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Standard roles cannot be deleted"));
    }

    // ----- The company keeps control of itself -----

    /**
     * Runs in a company of its own: it strips administrators, which would leave the
     * shared fixture of this class unusable for the other tests.
     */
    @Test
    @DisplayName("a company cannot be left without an active administrator")
    void lastAdministratorIsProtected() throws Exception {
        String ownerEmail = uniqueEmail("solo-owner");
        String ownerToken = registerCompany("Lockout Test Co", ownerEmail);
        long ownerId = number(getJson("/api/auth/me", ownerToken)
                .andReturn().getResponse().getContentAsString(), "$.id");

        // A deputy who may manage users but is not an administrator
        String deputyRole = postJson("/api/roles", ownerToken, """
                {"label":"Deputy","permissions":["USER_VIEW","USER_UPDATE","USER_DISABLE"]}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String deputyEmail = uniqueEmail("deputy");
        createUser(ownerToken, deputyEmail, number(deputyRole, "$.id"));
        String deputyToken = login(deputyEmail, "Password123");

        String ownerUsername = ownerEmail.substring(0, ownerEmail.indexOf('@'));

        // Taking the admin role away from the only administrator
        putJson("/api/users/" + ownerId, deputyToken, """
                {"firstName":"Test","lastName":"Admin","username":"%s",
                 "email":"%s","status":"ACTIVE","roleIds":[]}
                """.formatted(ownerUsername, ownerEmail))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("The company must keep at least one active administrator"));

        // Deactivating them is refused for the same reason
        patchJson("/api/users/" + ownerId + "/deactivate", deputyToken)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("The company must keep at least one active administrator"));

        // And the administrator is still there, untouched
        getJson("/api/users/" + ownerId, ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0].name").value("COMPANY_ADMIN"));
    }

    @Test
    @DisplayName("with a second administrator in place, the first one can be stepped down")
    void administratorCanStepDownOnceReplaced() throws Exception {
        String ownerEmail = uniqueEmail("handover-owner");
        String ownerToken = registerCompany("Handover Test Co", ownerEmail);
        long ownerId = number(getJson("/api/auth/me", ownerToken)
                .andReturn().getResponse().getContentAsString(), "$.id");

        String successorEmail = uniqueEmail("successor");
        createUser(ownerToken, successorEmail, roleId(ownerToken, "COMPANY_ADMIN"));
        String successorToken = login(successorEmail, "Password123");

        putJson("/api/users/" + ownerId, successorToken, """
                {"firstName":"Test","lastName":"Admin","username":"%s",
                 "email":"%s","status":"ACTIVE","roleIds":[]}
                """.formatted(ownerEmail.substring(0, ownerEmail.indexOf('@')), ownerEmail))
                .andExpect(status().isOk());

        // The former administrator now reaches nothing
        getJson("/api/users", ownerToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a role still held by someone cannot be deleted")
    void roleInUseCannotBeDeleted() throws Exception {
        deleteJson("/api/roles/" + assistantRoleId, adminToken)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("still assigned to")));
    }

    @Test
    @DisplayName("the permissions actually granted match the roles held, and nothing more")
    void effectivePermissionsAreTheUnionOfTheRoles() throws Exception {
        String session = getJson("/api/auth/me", assistantToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> permissions = JsonPath.read(session, "$.permissions");
        org.assertj.core.api.Assertions.assertThat(permissions)
                .containsExactlyInAnyOrder("ROLE_VIEW", "ROLE_CREATE", "ROLE_UPDATE",
                        "USER_VIEW", "USER_CREATE", "USER_UPDATE");
    }
}
