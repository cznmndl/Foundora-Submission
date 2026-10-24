package com.foundora.auth;

import com.foundora.database.entity.LoginOutcome;
import com.foundora.database.entity.UserProfile;
import com.foundora.database.repository.UserProfileRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class PinService {

    public static final int MAX_ATTEMPTS = 3;
    public static final Duration LOCKOUT = Duration.ofHours(24);

    public enum Status { NEEDS_SETUP, NEEDS_ENTRY, LOCKED }

    public record State(Status status, int attemptsLeft, Instant lockedUntil) {
    }

    public sealed interface Check {
        record Correct() implements Check {
        }

        record Wrong(int attemptsLeft) implements Check {
        }

        record Locked(Instant until) implements Check {
        }

        record NoPin() implements Check {
        }
    }

    private final UserProfileRepository profiles;
    private final ProfileService profileService;
    private final LoginHistory loginHistory;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PinService(UserProfileRepository profiles, ProfileService profileService, LoginHistory loginHistory,
                      Clock clock) {
        this.profiles = profiles;
        this.profileService = profileService;
        this.loginHistory = loginHistory;
        this.clock = clock;
    }

    public static String problem(String pin, String confirm) {
        if (pin == null || !pin.matches("[0-9]{4}")) {
            return "Your PIN must be exactly 4 digits.";
        }
        if (isEasy(pin)) {
            return "That PIN is too easy to guess. Avoid repeated digits like 1111 and runs like 1234.";
        }
        if (!pin.equals(confirm)) {
            return "The two PINs don't match. Please enter the same PIN twice.";
        }
        return null;
    }

    static boolean isEasy(String pin) {
        boolean same = true;
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < pin.length(); i++) {
            int step = pin.charAt(i) - pin.charAt(i - 1);
            same &= step == 0;
            up &= step == 1;
            down &= step == -1;
        }
        return same || up || down;
    }

    @Transactional
    public State state(FoundoraUser user) {
        UserProfile profile = profiles.findById(user.uid()).orElse(null);
        if (profile == null || !profile.hasPin()) {
            return new State(Status.NEEDS_SETUP, MAX_ATTEMPTS, null);
        }
        if (isLocked(profile)) {
            return new State(Status.LOCKED, 0, profile.getPinLockedUntil());
        }
        unlockIfExpired(profile);
        return new State(Status.NEEDS_ENTRY, MAX_ATTEMPTS - profile.getPinFailedAttempts(), null);
    }

    @Transactional
    public boolean create(FoundoraUser user, String pin, String confirm) {
        if (problem(pin, confirm) != null) {
            return false;
        }
        UserProfile profile = profiles.findForUpdate(user.uid()).orElseGet(() -> profileService.recordSignIn(user));
        if (profile.hasPin()) {
            return false;
        }
        profile.clearPin();
        profile.setPinHash(encoder.encode(pin));
        profile.setUpdatedAt(clock.instant());
        loginHistory.record(LoginOutcome.PIN_SET, user, null);
        return true;
    }

    @Transactional
    public Check verify(FoundoraUser user, String pin) {
        UserProfile profile = profiles.findForUpdate(user.uid()).orElse(null);
        if (profile == null || !profile.hasPin()) {
            return new Check.NoPin();
        }
        if (isLocked(profile)) {
            loginHistory.record(LoginOutcome.LOCKED, user, null);
            return new Check.Locked(profile.getPinLockedUntil());
        }
        unlockIfExpired(profile);
        int attempt = profile.getPinFailedAttempts() + 1;
        if (pin != null && pin.matches("[0-9]{4}") && encoder.matches(pin, profile.getPinHash())) {
            profile.setPinFailedAttempts(0);
            profile.setLastLoginAt(clock.instant());
            loginHistory.record(LoginOutcome.PIN_SUCCESS, user, attempt);
            return new Check.Correct();
        }
        profile.setPinFailedAttempts(attempt);
        if (attempt >= MAX_ATTEMPTS) {
            Instant until = clock.instant().plus(LOCKOUT);
            profile.setPinLockedUntil(until);
            loginHistory.record(LoginOutcome.LOCKED, user, attempt);
            return new Check.Locked(until);
        }
        loginHistory.record(LoginOutcome.PIN_FAILED, user, attempt);
        return new Check.Wrong(MAX_ATTEMPTS - attempt);
    }

    @Transactional
    public boolean requestReset(FoundoraUser user) {
        UserProfile profile = profiles.findForUpdate(user.uid()).orElse(null);
        if (profile == null || !profile.hasPin() || isLocked(profile)) {
            return false;
        }
        profile.setPinResetRequested(true);
        return true;
    }

    private boolean isLocked(UserProfile profile) {
        return profile.getPinLockedUntil() != null && clock.instant().isBefore(profile.getPinLockedUntil());
    }

    private static void unlockIfExpired(UserProfile profile) {
        if (profile.getPinLockedUntil() != null) {
            profile.setPinLockedUntil(null);
            profile.setPinFailedAttempts(0);
        }
    }
}
