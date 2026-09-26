package com.sales.smartBusiness.platform;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    @PostMapping("/login")
    public PlatformAuthResponse login(@Valid @RequestBody PlatformLoginRequest request) {
        return platformAuthService.login(request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    public PlatformSessionResponse me() {
        return platformAuthService.me();
    }
}
