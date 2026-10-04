package com.foundora.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps a school email to a role:
 * first_last@dlsl.edu.ph is a student; first.last@dlsl.edu.ph is an admin only if allowlisted.
 * Everything else is rejected.
 */
@Component
public class EmailRoleResolver {

    public static final String ALLOWED_DOMAIN = "dlsl.edu.ph";

    private final Set<String> adminEmails;

    public EmailRoleResolver(@Value("${foundora.auth.admin-emails:}") List<String> adminEmails) {
        this.adminEmails = adminEmails.stream()
                .map(EmailRoleResolver::normalize)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        for (String email : this.adminEmails) {
            if (!isAdminFormat(email)) {
                throw new IllegalStateException("foundora.auth.admin-emails entry is not a first.last@"
                        + ALLOWED_DOMAIN + " address: " + email);
            }
        }
    }

    public UserRole resolve(String email) {
        String normalized = normalize(email);
        String localPart = schoolLocalPart(normalized);
        if (localPart == null) {
            throw new LoginRejectedException("Please sign in with your @" + ALLOWED_DOMAIN + " Google account.");
        }

        boolean hasUnderscore = localPart.contains("_");
        boolean hasDot = localPart.contains(".");
        if (hasUnderscore && !hasDot) {
            return UserRole.STUDENT;
        }
        if (hasDot && !hasUnderscore) {
            if (adminEmails.contains(normalized)) {
                return UserRole.ADMIN;
            }
            throw new LoginRejectedException("This account is not on the Foundora admin allowlist.");
        }
        throw new LoginRejectedException("This account is not recognized as a student or admin account.");
    }

    private static boolean isAdminFormat(String email) {
        String localPart = schoolLocalPart(email);
        return localPart != null && localPart.contains(".") && !localPart.contains("_");
    }

    /** Returns the part before {@code @dlsl.edu.ph}, or null if the address is not exactly on that domain. */
    private static String schoolLocalPart(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@')) {
            return null;
        }
        if (!email.substring(at + 1).equals(ALLOWED_DOMAIN)) {
            return null;
        }
        return email.substring(0, at);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
