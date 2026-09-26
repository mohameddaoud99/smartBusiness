package com.sales.smartBusiness.platform;

import com.sales.smartBusiness.exception.InvalidCredentialsException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentPlatformAdmin;
import com.sales.smartBusiness.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlatformAuthService {

    /** Same message whichever half of the pair is wrong — no account enumeration. */
    private static final String REJECTED = "Invalid email or password";

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentPlatformAdmin currentPlatformAdmin;

    public PlatformAuthResponse login(PlatformLoginRequest request) {
        PlatformAdmin admin = platformAdminRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new InvalidCredentialsException(REJECTED));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new InvalidCredentialsException(REJECTED);
        }
        if (admin.getStatus() != PlatformAdminStatus.ACTIVE) {
            throw new InvalidCredentialsException("This account is not active.");
        }

        String token = jwtService.generateForPlatform(admin);
        return new PlatformAuthResponse(token, jwtService.expiresAt(), toSession(admin));
    }

    @Transactional(readOnly = true)
    public PlatformSessionResponse me() {
        PlatformAdmin admin = platformAdminRepository.findById(currentPlatformAdmin.id())
                .orElseThrow(() -> ResourceNotFoundException.of("Platform admin", currentPlatformAdmin.id()));
        return toSession(admin);
    }

    private PlatformSessionResponse toSession(PlatformAdmin admin) {
        PlatformSessionResponse session = new PlatformSessionResponse();
        session.setId(admin.getId());
        session.setEmail(admin.getEmail());
        return session;
    }
}
