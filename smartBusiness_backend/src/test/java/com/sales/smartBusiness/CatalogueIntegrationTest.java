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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack for the product catalogue: category nesting and cycle protection, brand and
 * category resolution on a product, reference generation, and default taxes — the parts
 * a Mockito test cannot see (real JSON, real column mapping, real foreign keys).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueIntegrationTest extends IntegrationTest {

    private String token;

    @BeforeAll
    void setUp() throws Exception {
        // The test database restarts its ids at 1 every run, but target/test-uploads survives
        // between runs — leftover photos of an earlier run would be counted as ours.
        deleteRecursively(UPLOADS_ROOT);
        token = registerCompany("Catalogue Test Co");
    }

    private static void deleteRecursively(Path root) throws java.io.IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    // ----- Categories -----

    @Test
    @DisplayName("a subcategory nests under its parent")
    void createsNestedCategory() throws Exception {
        String parent = postJson("/api/categories", token, """
                {"name":"Electronics"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long parentId = number(parent, "$.id");

        postJson("/api/categories", token, """
                {"name":"Phones","parentId":%d}
                """.formatted(parentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentId))
                .andExpect(jsonPath("$.parentName").value("Electronics"));
    }

    @Test
    @DisplayName("a category cannot be moved under its own descendant")
    void rejectsDeepCycle() throws Exception {
        String grandparent = postJson("/api/categories", token, """
                {"name":"Root A"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long grandparentId = number(grandparent, "$.id");

        String child = postJson("/api/categories", token, """
                {"name":"Child A","parentId":%d}
                """.formatted(grandparentId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long childId = number(child, "$.id");

        putJson("/api/categories/" + grandparentId, token, """
                {"name":"Root A","parentId":%d}
                """.formatted(childId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("own subcategories")));
    }

    @Test
    @DisplayName("a category still holding a product cannot be deleted")
    void rejectsDeletingCategoryInUse() throws Exception {
        String category = postJson("/api/categories", token, """
                {"name":"Accessories"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = number(category, "$.id");

        postJson("/api/products", token, """
                {"name":"Phone Case","kind":"GOOD","purpose":"SALE","unit":"PIECE","categoryId":%d}
                """.formatted(categoryId))
                .andExpect(status().isCreated());

        deleteJson("/api/categories/" + categoryId, token)
                .andExpect(status().isUnprocessableEntity());
    }

    // ----- Products -----

    @Test
    @DisplayName("a product gets a generated P-prefixed reference and resolves category, brand and taxes")
    void createsProductWithCatalogueLinks() throws Exception {
        String category = postJson("/api/categories", token, """
                {"name":"Beverages"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = number(category, "$.id");

        String brand = postJson("/api/brands", token, """
                {"name":"Delice"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long brandId = number(brand, "$.id");

        String taxes = getJson("/api/settings/taxes", token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> taxIds = com.jayway.jsonpath.JsonPath.read(taxes, "$[?(@.name == 'TVA 19%')].id");
        long taxId = taxIds.get(0).longValue();

        postJson("/api/products", token, """
                {"name":"Mineral Water 1.5L","kind":"GOOD","purpose":"SALE","unit":"LITER",
                 "categoryId":%d,"brandId":%d,"salePrice":1.200,"purchasePrice":0.800,
                 "taxIds":[%d]}
                """.formatted(categoryId, brandId, taxId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(matchesPattern("P-\\d{4}")))
                .andExpect(jsonPath("$.category.name").value("Beverages"))
                .andExpect(jsonPath("$.brand.name").value("Delice"))
                .andExpect(jsonPath("$.defaultTaxes[0].name").value("TVA 19%"))
                .andExpect(jsonPath("$.salePrice").value(1.2));
    }

    @Test
    @DisplayName("a service has no stock-related meaning but is still a valid product")
    void createsServiceProduct() throws Exception {
        postJson("/api/products", token, """
                {"name":"Installation","kind":"SERVICE","purpose":"SALE","unit":"HOUR"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SERVICE"))
                .andExpect(jsonPath("$.category").doesNotExist());
    }

    @Test
    @DisplayName("an explicit reference already used in the company is refused")
    void refusesDuplicateReference() throws Exception {
        postJson("/api/products", token, """
                {"name":"Gamma","kind":"GOOD","purpose":"BOTH","unit":"PIECE","reference":"SKU-1"}
                """).andExpect(status().isCreated());

        postJson("/api/products", token, """
                {"name":"Gamma bis","kind":"GOOD","purpose":"BOTH","unit":"PIECE","reference":"SKU-1"}
                """)
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a category from another company cannot be assigned to a product")
    void rejectsForeignCategory() throws Exception {
        String otherToken = registerCompany("Another Catalogue Co");
        String category = postJson("/api/categories", otherToken, """
                {"name":"Foreign"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long foreignCategoryId = number(category, "$.id");

        postJson("/api/products", token, """
                {"name":"Cross-tenant","kind":"GOOD","purpose":"SALE","unit":"PIECE","categoryId":%d}
                """.formatted(foreignCategoryId))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("products and customers are numbered independently")
    void productsAndCustomersHaveSeparateSequences() throws Exception {
        String customer = postJson("/api/customers", token, """
                {"type":"COMPANY","name":"A customer of the same company"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String product = postJson("/api/products", token, """
                {"name":"A product of the same company","kind":"GOOD","purpose":"SALE","unit":"PIECE"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(
                com.jayway.jsonpath.JsonPath.<String>read(customer, "$.reference")).startsWith("C-");
        org.assertj.core.api.Assertions.assertThat(
                com.jayway.jsonpath.JsonPath.<String>read(product, "$.reference")).startsWith("P-");
    }

    // ----- Photos -----

    private static final Path UPLOADS_ROOT = Paths.get("target/test-uploads/products");

    private long newProduct(String name) throws Exception {
        String product = postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"SALE","unit":"PIECE"}
                """.formatted(name)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return number(product, "$.id");
    }

    private MockMultipartFile png(int marker) {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[]{(byte) marker, 2, 3});
    }

    @Test
    @DisplayName("photos are stored on disk and come back as data URIs, the first being the cover")
    void addsPhotosToDisk() throws Exception {
        long id = newProduct("Photographed Product");

        postFile("/api/products/" + id + "/images", token, png(1)).andExpect(status().isOk());
        postFile("/api/products/" + id + "/images", token, png(2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].dataUri").value(
                        org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.imageDataUri").exists());

        try (var files = Files.list(UPLOADS_ROOT.resolve(String.valueOf(id)))) {
            assertThat(files.count()).as("photo files on disk").isEqualTo(2);
        }

        getJson("/api/products/" + id, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(2));
    }

    @Test
    @DisplayName("a fifth photo is refused")
    void refusesFifthPhoto() throws Exception {
        long id = newProduct("Overloaded Product");
        for (int i = 0; i < 4; i++) {
            postFile("/api/products/" + id + "/images", token, png(i)).andExpect(status().isOk());
        }

        postFile("/api/products/" + id + "/images", token, png(9))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("at most 4")));
    }

    @Test
    @DisplayName("the list carries the cover photo only, so the screen can show thumbnails")
    void listCarriesTheCoverOnly() throws Exception {
        long id = newProduct("Thumbnail Product");
        postFile("/api/products/" + id + "/images", token, png(5)).andExpect(status().isOk());
        postFile("/api/products/" + id + "/images", token, png(6)).andExpect(status().isOk());

        getJson("/api/products?search=Thumbnail", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].imageDataUri").value(
                        org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.content[0].images.length()").value(0));
    }

    @Test
    @DisplayName("removing a photo deletes its file and keeps the others")
    void removesOnePhoto() throws Exception {
        long id = newProduct("Photo To Remove");
        postFile("/api/products/" + id + "/images", token, png(1)).andExpect(status().isOk());
        String body = postFile("/api/products/" + id + "/images", token, png(2))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long firstImageId = number(body, "$.images[0].id");

        deleteJson("/api/products/" + id + "/images/" + firstImageId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(1));

        try (var files = Files.list(UPLOADS_ROOT.resolve(String.valueOf(id)))) {
            assertThat(files.count()).as("photo files left on disk").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("deleting a product removes its photo files")
    void deletingAProductRemovesItsPhotos() throws Exception {
        long id = newProduct("Short-lived Product");
        postFile("/api/products/" + id + "/images", token, png(1)).andExpect(status().isOk());

        deleteJson("/api/products/" + id, token).andExpect(status().isNoContent());

        Path dir = UPLOADS_ROOT.resolve(String.valueOf(id));
        if (Files.exists(dir)) {
            try (var files = Files.list(dir)) {
                assertThat(files.count()).isZero();
            }
        }
    }

    @Test
    @DisplayName("a non-image upload is refused")
    void rejectsNonImageUpload() throws Exception {
        long id = newProduct("No Photo");

        postFile("/api/products/" + id + "/images", token,
                new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only PNG, JPEG or WEBP images are allowed"));
    }
}
