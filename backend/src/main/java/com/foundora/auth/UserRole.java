package com.foundora.auth;

public enum UserRole {
    STUDENT("Student"),
    STAFF("Staff"),
    ADMIN("Admin");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean canModerate() {
        return this == ADMIN;
    }
}
