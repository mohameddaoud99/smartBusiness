package com.sales.smartBusiness.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private Instant expiresAt;
    private SessionResponse user;
}
