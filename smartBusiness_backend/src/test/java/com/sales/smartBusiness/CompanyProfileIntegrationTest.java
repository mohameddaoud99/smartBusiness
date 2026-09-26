package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the whole stack for the company profile screen, including the parts a
 * Mockito test cannot see: real multipart parsing and a real write to disk under
 * {@code smartbusiness.uploads.dir} (see src/test/resources/application.properties).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompanyProfileIntegrationTest extends IntegrationTest {

    private static final Path UPLOADS_ROOT = Paths.get("target/test-uploads/companies");

    private String token;
    private long companyId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Profile Test Co");
        companyId = number(getJson("/api/auth/me", token)
                .andReturn().getResponse().getContentAsString(), "$.companyId");
    }

    @Test
    @DisplayName("the administrator fills in the extended company profile")
    void updatesProfileFields() throws Exception {
        putJson("/api/company", token, """
                {"name":"Profile Test Co","email":"contact@profiletest.tn","phone":"+216 71 000 000",
                 "address":"12 Rue de Carthage","postalCode":"1002","city":"Tunis",
                 "taxId":"1234567A/A/M/000","currency":"TND"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxId").value("1234567A/A/M/000"))
                .andExpect(jsonPath("$.city").value("Tunis"))
                .andExpect(jsonPath("$.postalCode").value("1002"))
                .andExpect(jsonPath("$.currency").value("TND"));

        getJson("/api/company", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Tunis"));
    }

    @Test
    @DisplayName("currency must be a 3-letter ISO code")
    void rejectsInvalidCurrency() throws Exception {
        putJson("/api/company", token, """
                {"name":"Profile Test Co","currency":"dinars"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(
                        org.hamcrest.Matchers.containsString("3-letter ISO code")));
    }

    @Test
    @DisplayName("a logo is uploaded to disk and comes back as a data URI")
    void uploadsLogoToDisk() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[]{1, 2, 3, 4});

        postFile("/api/company/logo", token, file)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoDataUri").value(
                        org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

        Path expected = UPLOADS_ROOT.resolve(companyId + "/logo.png");
        assertThat(Files.exists(expected)).as("logo file on disk at %s", expected).isTrue();

        getJson("/api/company", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoDataUri").exists());
    }

    @Test
    @DisplayName("a non-image upload is refused")
    void rejectsNonImageUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        postFile("/api/company/logo", token, file)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only PNG, JPEG or WEBP images are allowed"));
    }

    @Test
    @DisplayName("removing the stamp deletes the file and clears the data URI")
    void removesStampFromDisk() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "stamp.png", "image/png", new byte[]{9, 9, 9});

        postFile("/api/company/stamp", token, file).andExpect(status().isOk());
        Path expected = UPLOADS_ROOT.resolve(companyId + "/stamp.png");
        assertThat(Files.exists(expected)).isTrue();

        deleteJson("/api/company/stamp", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stampDataUri").value(org.hamcrest.Matchers.nullValue()));

        assertThat(Files.exists(expected)).as("stamp file removed from disk").isFalse();
    }
}
