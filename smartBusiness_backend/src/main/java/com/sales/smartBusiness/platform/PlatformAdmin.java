package com.sales.smartBusiness.platform;

import com.sales.smartBusiness.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A platform operator — outside the tenant model entirely. Unlike {@code User}, this
 * entity carries no {@code company_id}: it is the one account type in the application
 * allowed to see and act across companies.
 */
@Entity
@Table(name = "platform_admins")
@Getter
@Setter
@NoArgsConstructor
public class PlatformAdmin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique regardless of case (index uk_platform_admins_email_ci). */
    @Column(nullable = false, length = 150)
    private String email;

    /** BCrypt hash — never exposed through the API. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformAdminStatus status = PlatformAdminStatus.ACTIVE;
}
