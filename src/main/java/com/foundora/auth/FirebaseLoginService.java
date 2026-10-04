package com.foundora.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Verifies a Firebase ID token on the server and turns it into a {@link FoundoraUser}.
 */
@Service
public class FirebaseLoginService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseLoginService.class);

    /** The ID token must come from a sign-in that just happened, not a long-lived browser session. */
    private static final Duration MAX_SIGN_IN_AGE = Duration.ofMinutes(5);

    private final FirebaseAuth firebaseAuth;
    private final EmailRoleResolver roleResolver;

    public FirebaseLoginService(FirebaseAuth firebaseAuth, EmailRoleResolver roleResolver) {
        this.firebaseAuth = firebaseAuth;
        this.roleResolver = roleResolver;
    }

    public FoundoraUser authenticate(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new LoginRejectedException("Missing sign-in token.");
        }

        FirebaseToken token;
        try {
            // Checks signature, expiry, audience/issuer for this project, and that the user is not revoked or disabled.
            token = firebaseAuth.verifyIdToken(idToken, true);
        } catch (FirebaseAuthException e) {
            log.warn("Rejected Firebase ID token: {}", e.getAuthErrorCode());
            throw new LoginRejectedException("Your sign-in could not be verified. Please try again.");
        }

        if (!"google.com".equals(signInProvider(token))) {
            throw new LoginRejectedException("Please sign in with Google.");
        }
        if (token.getEmail() == null || !token.isEmailVerified()) {
            throw new LoginRejectedException("Your Google account email is not verified.");
        }
        if (!isRecentSignIn(token)) {
            throw new LoginRejectedException("Your sign-in has expired. Please try again.");
        }

        UserRole role = roleResolver.resolve(token.getEmail());
        String email = token.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = token.getName() != null ? token.getName() : email;
        return new FoundoraUser(token.getUid(), email, name, role);
    }

    private static String signInProvider(FirebaseToken token) {
        if (token.getClaims().get("firebase") instanceof Map<?, ?> firebase) {
            return (String) firebase.get("sign_in_provider");
        }
        return null;
    }

    private static boolean isRecentSignIn(FirebaseToken token) {
        if (!(token.getClaims().get("auth_time") instanceof Number authTime)) {
            return false;
        }
        Instant signedInAt = Instant.ofEpochSecond(authTime.longValue());
        return signedInAt.isAfter(Instant.now().minus(MAX_SIGN_IN_AGE));
    }
}
