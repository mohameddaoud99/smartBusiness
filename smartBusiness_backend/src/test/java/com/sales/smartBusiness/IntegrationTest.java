package com.sales.smartBusiness;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for the tests that exercise the real HTTP stack: security filter chain,
 * {@code @PreAuthorize}, services and PostgreSQL.
 * <p>
 * Isolation and authorization cannot be proven with mocks — a repository that forgets
 * its {@code companyId} only shows up against a real database, which is the whole point
 * of these tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final String DATABASE = "smartbusiness_test";

    /*
     * Recreated once per test run, before any Spring context starts. Flyway then builds
     * the schema, so every run starts from a known-empty database and no manual setup
     * step is needed on a fresh clone. The development database is never touched.
     */
    static {
        recreateTestDatabase();
    }

    @Autowired protected MockMvc mockMvc;

    private static void recreateTestDatabase() {
        String url = "jdbc:postgresql://localhost:5432/postgres";
        String user = System.getenv().getOrDefault("DB_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            // FORCE closes any leftover session, e.g. a psql window left open on it
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + DATABASE);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not prepare the test database. Is PostgreSQL running on localhost:5432?", ex);
        }
    }

    // ----- Fixtures -----

    /** Signs a brand new company up and returns its administrator's token. */
    protected String registerCompany(String companyName) throws Exception {
        return registerCompany(companyName, uniqueEmail("admin"));
    }

    protected String registerCompany(String companyName, String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s","firstName":"Test","lastName":"Admin",
                                 "email":"%s","password":"Password123"}
                                """.formatted(companyName, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.token");
    }

    /** The bootstrap platform admin — see src/test/resources/application.properties. */
    protected String platformLogin() throws Exception {
        String response = mockMvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"platform-admin@test.local","password":"PlatformPass123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.token");
    }

    protected String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.token");
    }

    /** Creates a user holding the given roles. Their password is always Password123. */
    protected long createUser(String token, String email, Long... roleIds) throws Exception {
        String ids = Arrays.stream(roleIds).map(String::valueOf).collect(Collectors.joining(","));
        String username = email.substring(0, email.indexOf('@'));

        String response = postJson("/api/users", token, """
                {"firstName":"Test","lastName":"User","username":"%s","email":"%s",
                 "status":"ACTIVE","password":"Password123","roleIds":[%s]}
                """.formatted(username, email, ids))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return number(response, "$.id");
    }

    /** Id of a role of the caller's company, looked up by its stable name. */
    protected long roleId(String token, String roleName) throws Exception {
        String response = getJson("/api/roles", token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // A JsonPath filter always yields a list, even when it matches a single role
        List<Number> ids = JsonPath.read(response, "$[?(@.name == '" + roleName + "')].id");
        if (ids.isEmpty()) {
            throw new IllegalStateException("No role named " + roleName + " in this company");
        }
        return ids.get(0).longValue();
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    // ----- Request helpers -----

    protected ResultActions getJson(String url, String token) throws Exception {
        return mockMvc.perform(bearer(get(url), token));
    }

    protected ResultActions deleteJson(String url, String token) throws Exception {
        return mockMvc.perform(bearer(delete(url), token));
    }

    protected ResultActions patchJson(String url, String token) throws Exception {
        return mockMvc.perform(bearer(patch(url), token));
    }

    protected ResultActions patchJson(String url, String token, String body) throws Exception {
        return mockMvc.perform(bearer(patch(url), token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions postJson(String url, String token, String body) throws Exception {
        return mockMvc.perform(bearer(post(url), token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions putJson(String url, String token, String body) throws Exception {
        return mockMvc.perform(bearer(put(url), token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions postFile(String url, String token, MockMultipartFile file) throws Exception {
        var request = multipart(url).file(file);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    /** A null token means an anonymous request. */
    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }
}
