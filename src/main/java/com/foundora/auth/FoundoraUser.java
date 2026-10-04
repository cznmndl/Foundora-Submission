package com.foundora.auth;

import java.io.Serializable;

/**
 * The signed-in user, stored in the HTTP session after the Firebase ID token has been verified.
 */
public record FoundoraUser(String uid, String email, String name, UserRole role) implements Serializable {

    public String homePath() {
        return role == UserRole.ADMIN ? "/admin" : "/";
    }
}
