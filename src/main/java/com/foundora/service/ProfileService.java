package com.foundora.service;

import com.foundora.auth.FoundoraUser;
import com.foundora.auth.UserRole;
import com.foundora.model.UserProfile;
import com.foundora.repository.UserProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Profiles: created at sign-in, completed once on the setup page, editable on My Profile. */
@Service
public class ProfileService {

    private final UserProfileRepository profiles;
    private final Clock clock;

    public ProfileService(UserProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * Creates the profile on first sign-in, or refreshes its name, email and role from the Google account.
     * The details (course, department, ...) are left as they are.
     */
    @Transactional
    public UserProfile recordSignIn(FoundoraUser user) {
        Instant now = clock.instant();
        UserProfile profile = profiles.findById(user.uid()).orElse(null);
        if (profile == null) {
            // Same email under a new Firebase UID (e.g. the account was re-created): carry the details over.
            UserProfile previous = profiles.findByEmailIgnoreCase(user.email()).orElse(null);
            profile = new UserProfile(user.uid(), user.email(), user.name(), user.role(), now);
            if (previous != null) {
                profile.setCourse(previous.getCourse());
                profile.setYearLevel(previous.getYearLevel());
                profile.setSection(previous.getSection());
                profile.setDepartment(previous.getDepartment());
                profiles.delete(previous);
                profiles.flush();
            }
        }
        profile.setEmail(user.email());
        profile.setFullName(user.name());
        profile.setRole(user.role());
        profile.setUpdatedAt(now);
        return profiles.save(profile);
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> find(FoundoraUser user) {
        return profiles.findById(user.uid());
    }

    @Transactional(readOnly = true)
    public boolean isComplete(FoundoraUser user) {
        return profiles.findById(user.uid()).map(p -> p.isCompleteFor(user.role())).orElse(false);
    }

    /** Saves the details for this role; the other role's details are cleared so the profile stays consistent. */
    @Transactional
    public UserProfile save(FoundoraUser user, ProfileForm form) {
        UserProfile profile = profiles.findById(user.uid()).orElseGet(() -> recordSignIn(user));
        if (user.role() == UserRole.STUDENT) {
            profile.setCourse(form.resolvedCourse());
            profile.setYearLevel(form.getYearLevel());
            profile.setSection(form.getSection());
            profile.setDepartment(null);
        } else {
            profile.setDepartment(form.getDepartment());
            profile.setCourse(null);
            profile.setYearLevel(null);
            profile.setSection(null);
        }
        profile.setRole(user.role());
        profile.setUpdatedAt(clock.instant());
        return profiles.save(profile);
    }

    /** Profiles by (lower-case) email, for showing admins who posted or commented. */
    @Transactional(readOnly = true)
    public Map<String, UserProfile> byEmails(Collection<String> emails) {
        List<String> lower = emails.stream().filter(e -> e != null).map(e -> e.toLowerCase(Locale.ROOT))
                .distinct().toList();
        if (lower.isEmpty()) {
            return Map.of();
        }
        return profiles.findByEmailIn(lower).stream()
                .collect(Collectors.toMap(p -> p.getEmail().toLowerCase(Locale.ROOT), Function.identity(),
                        (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> byEmail(String email) {
        return email == null || email.isBlank() ? Optional.empty() : profiles.findByEmailIgnoreCase(email.trim());
    }

    /** Up to 8 registered people whose name or email contains the text. */
    @Transactional(readOnly = true)
    public List<UserProfile> search(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return profiles.search(text.trim().toLowerCase(Locale.ROOT), PageRequest.of(0, 8));
    }
}
