package com.sales.smartBusiness.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndReferenceIgnoreCase(Long companyId, String reference);

    boolean existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(Long companyId, String reference, Long id);

    /**
     * Blocks deletion while a sales document still points here. Lives here rather than in
     * the sales repository so the customer feature never depends on the sales feature
     * (sales depends on customers, not the other way round).
     */
    @Query("SELECT COUNT(d) FROM SalesDocument d WHERE d.customer.id = :customerId")
    long countSalesDocumentsUsing(@Param("customerId") Long customerId);

    /**
     * List screen: free-text search plus an optional type filter, always scoped to the
     * caller's company.
     *
     * {@code search} is always a lower-case LIKE pattern ("%" when nothing was typed) —
     * PostgreSQL cannot infer the type of a NULL parameter inside LOWER(), so the
     * service normalises it rather than passing null here. The {@code type} enum is
     * safe to leave nullable: Hibernate binds it with a known type.
     */
    @Query(value = """
            SELECT c FROM Customer c
            WHERE c.company.id = :companyId
              AND LOWER(CONCAT(c.name, ' ', COALESCE(c.reference, ''), ' ',
                        COALESCE(c.email, ''), ' ', COALESCE(c.contactName, ''))) LIKE :search
              AND (:type IS NULL OR c.type = :type)
            """,
            countQuery = """
            SELECT COUNT(c) FROM Customer c
            WHERE c.company.id = :companyId
              AND LOWER(CONCAT(c.name, ' ', COALESCE(c.reference, ''), ' ',
                        COALESCE(c.email, ''), ' ', COALESCE(c.contactName, ''))) LIKE :search
              AND (:type IS NULL OR c.type = :type)
            """)
    Page<Customer> search(@Param("companyId") Long companyId,
                          @Param("search") String search,
                          @Param("type") CustomerType type,
                          Pageable pageable);
}
