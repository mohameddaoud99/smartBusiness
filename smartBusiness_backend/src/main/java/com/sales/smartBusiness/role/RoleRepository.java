package com.sales.smartBusiness.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Role> findByCompanyIdAndName(Long companyId, String name);

    List<Role> findByCompanyIdOrderByLabelAsc(Long companyId);

    List<Role> findByCompanyIdAndIdIn(Long companyId, Set<Long> ids);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    /**
     * How many users hold this role — blocks the deletion of a role still in use.
     * Lives here rather than in UserRepository so the role feature never depends on the
     * user feature (users depend on roles, not the other way round).
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countHolders(@Param("roleId") Long roleId);

    /** Used at startup to re-apply the SystemRole definitions across all companies. */
    List<Role> findByNameAndSystemTrue(String name);
}
