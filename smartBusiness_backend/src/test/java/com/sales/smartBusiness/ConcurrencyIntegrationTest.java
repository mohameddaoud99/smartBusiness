package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two requests at the same moment must not both pass a check made before the write: the last pieces of a strict good
 * are sold once, and the remainder of an order is delivered once. Each scenario fires its requests together and counts
 * the statuses - exactly one may succeed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyIntegrationTest extends IntegrationTest {

    private String token;
    private long mainWarehouse;
    private long customerId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Concurrency Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Concurrent"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Runs the calls at the same moment and returns the HTTP status of each. */
    private List<Integer> together(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return call.call();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private long good(boolean allowNegative) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"Item-%s","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":%b}
                """.formatted(UUID.randomUUID().toString().substring(0, 8), allowNegative)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("two exits of the last pieces of a strict good at once: one goes through, the other is refused")
    void lastPiecesAreSoldOnce() throws Exception {
        long product = good(false);
        postJson("/api/stock/movements", token, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":5,"reason":"test"}
                """.formatted(product, mainWarehouse)).andExpect(status().isCreated());

        Callable<Integer> exit = () -> postJson("/api/stock/movements", token, """
                {"type":"EXIT","productId":%d,"warehouseId":%d,"quantity":5,"reason":"race"}
                """.formatted(product, mainWarehouse)).andReturn().getResponse().getStatus();
        List<Integer> statuses = together(List.of(exit, exit, exit));

        assertThat(statuses).containsExactlyInAnyOrder(201, 422, 422);
        getJson("/api/stock/levels?search=Item", token).andExpect(status().isOk());
    }

    @Test
    @DisplayName("two delivery notes for the same remainder issued at once: only one is issued")
    void remainderIsDeliveredOnce() throws Exception {
        long product = good(true);
        long order = number(postJson("/api/sales-documents", token, """
                {"type":"SALES_ORDER","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":10,"unitPrice":10}]}
                """.formatted(customerId, product)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/sales-documents/" + order + "/issue", token, "").andExpect(status().isOk());
        postJson("/api/sales-documents/" + order + "/status", token, "{\"status\":\"CONFIRMED\"}").andExpect(status().isOk());

        // two drafts, each for the whole 10
        long first = number(postJson("/api/sales-documents/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        long second = number(postJson("/api/sales-documents/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        List<Integer> statuses = together(List.of(
                () -> postJson("/api/sales-documents/" + first + "/issue", token, "").andReturn().getResponse().getStatus(),
                () -> postJson("/api/sales-documents/" + second + "/issue", token, "").andReturn().getResponse().getStatus()));

        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        getJson("/api/sales-documents/" + order, token)
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].fulfilledQuantity").value(10.0))
                .andExpect(jsonPath("$.lines[0].remainingQuantity").value(0.0));
    }
}
