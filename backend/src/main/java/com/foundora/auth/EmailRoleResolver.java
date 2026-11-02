package com.foundora.auth;

import com.foundora.database.entity.LoginOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
            if (schoolLocalPart(email) == null) {
                throw new IllegalStateException("foundora.auth.admin-emails entry is not an @"
                        + ALLOWED_DOMAIN + " address: " + email);
            }
        }
    }

    public UserRole resolve(String email) {
        String normalized = normalize(email);
        String localPart = schoolLocalPart(normalized);
        if (localPart == null) {
            throw new LoginRejectedException("Please sign in with your @" + ALLOWED_DOMAIN + " Google account.",
                    LoginOutcome.REJECTED_DOMAIN);
        }

        if (adminEmails.contains(normalized)) {
            return UserRole.ADMIN;
        }

        boolean hasUnderscore = localPart.contains("_");
        boolean hasDot = localPart.contains(".");
        if (hasUnderscore && hasDot) {
            throw new LoginRejectedException("This account is not recognized as a student or staff account.",
                    LoginOutcome.REJECTED_DOMAIN);
        }
        return hasUnderscore ? UserRole.STUDENT : UserRole.STAFF;
    }

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
