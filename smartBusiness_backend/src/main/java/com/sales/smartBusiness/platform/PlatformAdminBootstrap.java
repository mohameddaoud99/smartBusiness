package com.sales.smartBusiness.platform;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the very first platform admin from the environment, so there is a way into
 * the platform portal without a manual database step on a fresh install. Runs once:
 * if any platform admin already exists, it does nothing, so it never resets a password
 * an admin has since changed.
 */
@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${smartbusiness.platform-admin.email:}")
    private String bootstrapEmail;

    @Value("${smartbusiness.platform-admin.password:}")
    private String bootstrapPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createFirstAdminIfNone() {
        if (platformAdminRepository.count() > 0) {
            return;
        }
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            log.warn("No platform admin exists yet, and PLATFORM_ADMIN_EMAIL / PLATFORM_ADMIN_PASSWORD "
                    + "are not set — the platform portal has no way in until one is configured.");
            return;
        }

        PlatformAdmin admin = new PlatformAdmin();
        admin.setEmail(bootstrapEmail.trim());
        // Trimmed like the email: `${VAR:default}` keeps a stray space after the colon
        // as part of the default value, so an untrimmed password silently differs from
        // what anyone would actually type in.
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword.trim()));
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        platformAdminRepository.save(admin);

        log.info("Created the first platform admin account for {}", admin.getEmail());
    }
}
