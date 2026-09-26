package com.sales.smartBusiness.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Email is the login identifier, so it is looked up without a company.
     * Company and roles are fetched with the user: the authentication filter reads them
     * outside any transaction.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.company LEFT JOIN FETCH u.roles WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u JOIN FETCH u.company LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByCompanyIdAndUsernameIgnoreCase(Long companyId, String username);

    boolean existsByCompanyIdAndUsernameIgnoreCaseAndIdNot(Long companyId, String username, Long id);

    /**
     * One query for the list screen: free-text search plus optional status/role filters,
     * always scoped to the caller's company.
     *
     * `search` is always a lower-case LIKE pattern ("%" when the user typed nothing) —
     * PostgreSQL cannot infer the type of a NULL parameter inside LOWER(), so the
     * service normalises it instead of passing null here.
     */
    @Query(value = """
            SELECT DISTINCT u FROM User u
            LEFT JOIN u.roles r
            WHERE u.company.id = :companyId
              AND LOWER(CONCAT(u.firstName, ' ', u.lastName, ' ', u.username, ' ', u.email)) LIKE :search
              AND (:status IS NULL OR u.status = :status)
              AND (:roleId IS NULL OR r.id = :roleId)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u) FROM User u
            LEFT JOIN u.roles r
            WHERE u.company.id = :companyId
              AND LOWER(CONCAT(u.firstName, ' ', u.lastName, ' ', u.username, ' ', u.email)) LIKE :search
              AND (:status IS NULL OR u.status = :status)
              AND (:roleId IS NULL OR r.id = :roleId)
            """)
    Page<User> search(@Param("companyId") Long companyId,
                      @Param("search") String search,
                      @Param("status") UserStatus status,
                      @Param("roleId") Long roleId,
                      Pageable pageable);

    /**
     * Lock-out guard: is there another active holder of this role left in the company?
     */
    @Query("""
            SELECT COUNT(DISTINCT u) FROM User u
            JOIN u.roles r
            WHERE u.company.id = :companyId
              AND u.status = :status
              AND r.name = :roleName
              AND u.id <> :excludedId
            """)
    long countOtherActiveHoldersOfRole(@Param("companyId") Long companyId,
                                       @Param("roleName") String roleName,
                                       @Param("status") UserStatus status,
                                       @Param("excludedId") Long excludedId);
}
