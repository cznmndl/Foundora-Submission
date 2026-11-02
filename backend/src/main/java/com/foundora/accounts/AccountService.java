package com.foundora.accounts;

import com.foundora.auth.FoundoraUser;
import com.foundora.auth.LoginHistory;
import com.foundora.config.Formats;
import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import com.foundora.database.entity.LoginOutcome;
import com.foundora.database.entity.UserProfile;
import com.foundora.database.repository.ActivityLogRepository;
import com.foundora.database.repository.UserProfileRepository;
import com.foundora.moderation.ModerationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService {

    public record Account(UserProfile profile, boolean locked, Instant lockedUntil) {
    }

    private static final int RESULTS = 20;
    private static final int CANDIDATES = 500;

    private final UserProfileRepository profiles;
    private final ActivityLogRepository activityLog;
    private final LoginHistory loginHistory;
    private final Formats fmt;
    private final Clock clock;

    public AccountService(UserProfileRepository profiles, ActivityLogRepository activityLog,
                          LoginHistory loginHistory, Formats fmt, Clock clock) {
        this.profiles = profiles;
        this.activityLog = activityLog;
        this.loginHistory = loginHistory;
        this.fmt = fmt;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Account> search(String text) {
        if (text == null || text.isBlank()) {
            return profiles.findByPinLockedUntilAfterOrderByPinLockedUntilAsc(clock.instant()).stream()
                    .map(this::account).toList();
        }
        List<String> words = List.of(text.trim().toLowerCase(Locale.ROOT).split("\\s+"));
        String longest = words.stream().max(Comparator.comparingInt(String::length)).orElseThrow();
        return profiles.search(longest, PageRequest.of(0, CANDIDATES)).stream()
                .filter(p -> words.stream().allMatch(word -> matches(p, word)))
                .limit(RESULTS)
                .map(this::account)
                .toList();
    }

    private static boolean matches(UserProfile profile, String word) {
        return profile.getFullName().toLowerCase(Locale.ROOT).contains(word)
                || profile.getEmail().toLowerCase(Locale.ROOT).contains(word);
    }

    @Transactional
    public UserProfile resetPin(FoundoraUser admin, String uid) {
        requireAdmin(admin);
        UserProfile profile = find(uid);
        if (isLocked(profile)) {
            throw new ModerationException("This account is locked until " + fmt.dateTime(profile.getPinLockedUntil())
                    + ". Unlock it first.");
        }
        if (!profile.hasPin()) {
            throw new ModerationException(profile.getFullName() + " has no PIN yet. They'll create one at their "
                    + "next sign-in.");
        }
        profile.clearPin();
        profile.setUpdatedAt(clock.instant());
        record(profile, admin, LoginOutcome.PIN_RESET, ActivityAction.PIN_RESET);
        return profile;
    }

    @Transactional
    public UserProfile unlock(FoundoraUser admin, String uid) {
        requireAdmin(admin);
        UserProfile profile = find(uid);
        if (!isLocked(profile)) {
            throw new ModerationException(profile.getFullName() + "'s account is not locked.");
        }
        profile.setPinFailedAttempts(0);
        profile.setPinLockedUntil(null);
        profile.setUpdatedAt(clock.instant());
        record(profile, admin, LoginOutcome.ACCOUNT_UNLOCKED, ActivityAction.ACCOUNT_UNLOCKED);
        return profile;
    }

    private void record(UserProfile profile, FoundoraUser admin, LoginOutcome outcome, ActivityAction action) {
        loginHistory.record(outcome, profile.getUid(), profile.getEmail(), null);
        activityLog.save(new ActivityLogEntry(profile, action, admin.email(), admin.name(), clock.instant()));
    }

    private UserProfile find(String uid) {
        return profiles.findForUpdate(uid == null ? "" : uid)
                .orElseThrow(() -> new ModerationException("That account no longer exists."));
    }

    private Account account(UserProfile profile) {
        return new Account(profile, isLocked(profile), isLocked(profile) ? profile.getPinLockedUntil() : null);
    }

    private boolean isLocked(UserProfile profile) {
        return profile.getPinLockedUntil() != null && clock.instant().isBefore(profile.getPinLockedUntil());
    }

    private static void requireAdmin(FoundoraUser admin) {
        if (admin == null || !admin.role().canModerate()) {
            throw new AccessDeniedException("Only admins can reset PINs or unlock accounts.");
        }
    }
}
