package com.sales.smartBusiness.role;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.EnumSet;
import java.util.Set;

/**
 * A job function inside a company: "what is this user allowed to do?".
 * A role never carries a branch.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Stable key: the SystemRole name, or the custom name chosen by the administrator. */
    @Column(nullable = false, length = 50)
    private String name;

    /** What the administrator reads on screen, e.g. "Sales Manager". */
    @Column(nullable = false, length = 80)
    private String label;

    @Column(length = 255)
    private String description;

    /** Shipped with the application: cannot be renamed, edited or deleted. */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    /**
     * Eager because a role's permissions are needed on every authenticated request,
     * batched so loading a page of roles stays a single extra query.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", length = 40, nullable = false)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);
}
