package com.sales.smartBusiness.user;

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    /** Blocked by the system rather than by an administrator (kept for the login policy). */
    LOCKED
}
