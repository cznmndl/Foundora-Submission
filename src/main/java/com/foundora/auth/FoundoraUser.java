package com.foundora.auth;

import java.io.Serializable;
import java.util.Locale;

public record FoundoraUser(String uid, String email, String name, UserRole role) implements Serializable {

    public String homePath() {
        return role == UserRole.ADMIN ? "/admin" : "/";
    }

    public boolean isAdmin() {
        return role.canModerate();
    }

    public String initial() {
        return initialOf(name);
    }

    public static String initialOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
