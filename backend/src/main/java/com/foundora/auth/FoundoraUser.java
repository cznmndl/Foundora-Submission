package com.foundora.auth;

import java.io.Serializable;
import java.util.Locale;

/**
 * The signed-in user, stored in the HTTP session after the Firebase ID token has been verified.
 */
public record FoundoraUser(String uid, String email, String name, UserRole role) implements Serializable {

    public String homePath() {
        return role == UserRole.ADMIN ? "/admin" : "/";
    }

    public boolean isAdmin() {
        return role.canModerate();
    }

    public String firstName() {
        if (name == null || name.isBlank()) {
            return "";
        }
        String first = name.trim().split("\\s+")[0];
        return first.substring(0, 1).toUpperCase(Locale.ROOT) + first.substring(1).toLowerCase(Locale.ROOT);
    }

    /** First letter of the name, shown in the avatar circle. */
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
