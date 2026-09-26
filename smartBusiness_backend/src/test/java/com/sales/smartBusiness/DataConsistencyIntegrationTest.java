package com.sales.smartBusiness;

import com.sales.smartBusiness.customer.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The guards that only a real database can prove: optimistic locking, case-insensitive
 * unique indexes, and customer codes drawn from the locked numbering sequence.
 */
class DataConsistencyIntegrationTest extends IntegrationTest {

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("customer codes follow the CUSTOMER numbering settings of the company")
    void customerReferenceFollowsNumbering() throws Exception {
        String token = registerCompany("Numbering Co");

        getJson("/api/settings/numbering", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.documentType == 'CUSTOMER')].preview").value("C-0001"));

        putJson("/api/settings/numbering/CUSTOMER", token, """
                {"prefix":"CLI","padding":3,"includeYear":false,"nextValue":7,"active":true}
                """)
                .andExpect(status().isOk());

        postJson("/api/customers", token, """
                {"type":"COMPANY","name":"First"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("CLI-007"));
        postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Second"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("CLI-008"));
    }

    @Test
    @DisplayName("the database refuses a customer code that differs only by case")
    void referenceUniquenessIgnoresCase() throws Exception {
        String token = registerCompany("Case Co");
        String reference = "VIP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        String body = postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Upper","reference":"%s"}
                """.formatted(reference))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long companyId = jdbcTemplate.queryForObject(
                "SELECT company_id FROM customers WHERE id = ?", Long.class, number(body, "$.id"));

        // Straight to the table, past the service check — only the index stands in the way
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO customers (company_id, type, reference, name, created_at, updated_at)
                VALUES (?, 'COMPANY', ?, 'Lower', NOW(), NOW())
                """, companyId, reference.toLowerCase()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("of two concurrent edits of the same record, the second one fails")
    void concurrentEditIsRejected() throws Exception {
        String token = registerCompany("Locking Co");
        String body = postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Original"}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = number(body, "$.id");

        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Customer seenByFirst = first.find(Customer.class, id);
            Customer seenBySecond = second.find(Customer.class, id);

            seenByFirst.setName("Edited by the first user");
            first.getTransaction().commit();

            seenBySecond.setName("Edited by the second user");
            assertThatThrownBy(() -> second.getTransaction().commit())
                    .satisfies(error -> assertThat(causeChainHas(error, OptimisticLockException.class)).isTrue());
        } finally {
            first.close();
            second.close();
        }

        String name = jdbcTemplate.queryForObject("SELECT name FROM customers WHERE id = ?", String.class, id);
        assertThat(name).isEqualTo("Edited by the first user");
    }

    private static boolean causeChainHas(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
