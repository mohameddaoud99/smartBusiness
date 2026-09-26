package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The login endpoint has no other rate limit: a wrong password past the threshold locks the account out for a
 * while, so guessing forever is not free. A low threshold (3) is set for this class only — the default (5) is
 * exercised through the property's own default.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "smartbusiness.login.lockout-threshold=3")
class LoginLockoutIntegrationTest extends IntegrationTest {

    private String adminToken;
    private String email;

    @BeforeAll
    void setUp() throws Exception {
        adminToken = registerCompany("Lockout Test Co", "owner@lockout-test.tn");
        email = "owner@lockout-test.tn";
    }

    private void wrongPassword() throws Exception {
        postJson("/api/auth/login", null, """
                {"email":"%s","password":"NotTheRightOne1"}
                """.formatted(email)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("locks the account out after the threshold of wrong passwords, even with the right one")
    void locksOutAfterThreshold() throws Exception {
        // the 3rd wrong password (the threshold) is what locks the account - the response still says which
        // half was wrong, not yet that it is locked: only the NEXT attempt sees the lock
        wrongPassword();
        wrongPassword();
        wrongPassword();

        // locked out now: even the correct password is refused
        postJson("/api/auth/login", null, """
                {"email":"%s","password":"Password123"}
                """.formatted(email))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Too many failed attempts")));
    }

    @Test
    @DisplayName("a correct password before the threshold resets the count of wrong ones")
    void successResetsTheCount() throws Exception {
        String freshEmail = "reset@lockout-test.tn";
        registerCompany("Reset Test Co", freshEmail);

        postJson("/api/auth/login", null, """
                {"email":"%s","password":"WrongOne1"}
                """.formatted(freshEmail)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", null, """
                {"email":"%s","password":"Password123"}
                """.formatted(freshEmail)).andExpect(status().isOk());

        // two more wrong ones: still under the threshold of 3, since the count was reset
        postJson("/api/auth/login", null, """
                {"email":"%s","password":"WrongOne1"}
                """.formatted(freshEmail)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", null, """
                {"email":"%s","password":"Password123"}
                """.formatted(freshEmail))
                .andExpect(status().isOk());
    }
}
