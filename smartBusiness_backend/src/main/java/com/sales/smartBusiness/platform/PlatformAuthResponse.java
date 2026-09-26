package com.sales.smartBusiness.platform;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class PlatformAuthResponse {

    private String token;
    private Instant expiresAt;
    private PlatformSessionResponse admin;
}
