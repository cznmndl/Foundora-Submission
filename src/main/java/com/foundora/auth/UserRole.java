package com.foundora.auth;

public enum UserRole {
    STUDENT,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
