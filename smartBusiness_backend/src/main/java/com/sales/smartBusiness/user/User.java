package com.sales.smartBusiness.user;

import com.sales.smartBusiness.branch.Branch;
import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.role.Permission;
import com.sales.smartBusiness.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * Which site this user is based at. Organisational only — it never scopes what
     * they may do; permissions always apply to the whole company. Left null for a
     * user who is not tied to a single site (e.g. a head-office administrator).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false, length = 60)
    private String firstName;

    @Column(nullable = false, length = 60)
    private String lastName;

    /** Display name, unique inside the company only. */
    @Column(nullable = false, length = 50)
    private String username;

    /** Login identifier, globally unique regardless of case (index uk_users_email_ci). */
    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    /** BCrypt hash — never exposed through the API. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * A user may hold several roles; the effective permissions are their union.
     * Batched so listing a page of users costs one extra query, not one per row.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @BatchSize(size = 50)
    private Set<Role> roles = new HashSet<>();

    private LocalDateTime lastLoginAt;

    /** Reset to 0 on a successful sign-in; a wrong password past the threshold sets {@link #lockedUntil}. */
    @Column(nullable = false)
    private int failedLoginAttempts;

    /** Null until too many wrong passwords in a row lock the account out for a while. */
    private LocalDateTime lockedUntil;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Effective permissions: everything granted by any of the roles, minus what belongs to a module the company
     * may not use. Every {@code @PreAuthorize} reads these, so a module a platform admin switched off is refused
     * by every one of its endpoints at once — and the session the frontend gets says the same.
     */
    public Set<Permission> collectPermissions() {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> permissions.addAll(role.getPermissions()));
        permissions.removeIf(permission -> !company.allows(permission));
        return permissions;
    }

    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(role -> role.getName().equals(roleName));
    }

    // ----- Login lockout -----

    public boolean isLockedOut(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Locks the account for {@code lockoutDuration} once {@code threshold} wrong passwords have been given in a row. */
    public void recordLoginFailure(int threshold, Duration lockoutDuration, LocalDateTime now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= threshold) {
            lockedUntil = now.plus(lockoutDuration);
        }
    }

    public void recordLoginSuccess(LocalDateTime now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }
}
