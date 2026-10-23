package com.foundora.auth;

import com.foundora.database.entity.Community;
import com.foundora.database.entity.UserProfile;
import com.foundora.database.repository.UserProfileRepository;
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

@Service
public class ProfileService {

    private final UserProfileRepository profiles;
    private final Clock clock;

    public ProfileService(UserProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public UserProfile recordSignIn(FoundoraUser user) {
        Instant now = clock.instant();
        UserProfile profile = profiles.findById(user.uid()).orElse(null);
        if (profile == null) {
            UserProfile previous = profiles.findByEmailIgnoreCase(user.email()).orElse(null);
            profile = new UserProfile(user.uid(), user.email(), user.name(), user.role(), now);
            if (previous != null) {
                profile.setCommunity(previous.getCommunity());
                profile.setCourse(previous.getCourse());
                profile.setYearLevel(previous.getYearLevel());
                profile.setGradeLevel(previous.getGradeLevel());
                profile.setSection(previous.getSection());
                profile.setDepartment(previous.getDepartment());
                profile.copyPinFrom(previous);
                profiles.delete(previous);
                profiles.flush();
            }
        }
        profile.setEmail(user.email());
        profile.setFullName(user.name());
        profile.setRole(user.role());
        if (profile.isPinResetRequested()) {
            profile.clearPin();
        }
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

    @Transactional
    public UserProfile save(FoundoraUser user, ProfileForm form) {
        UserProfile profile = profiles.findById(user.uid()).orElseGet(() -> recordSignIn(user));
        if (user.role() == UserRole.STUDENT) {
            Community community = form.resolvedCommunity();
            profile.setCommunity(community);
            profile.setSection(form.resolvedSection());
            profile.setDepartment(null);
            if (community.isCollege()) {
                profile.setCourse(form.getCourse());
                profile.setYearLevel(form.getYearLevel());
                profile.setGradeLevel(null);
            } else {
                profile.setGradeLevel(form.getGradeLevel());
                profile.setCourse(null);
                profile.setYearLevel(null);
            }
        } else {
            profile.setDepartment(form.getDepartment());
            profile.setCommunity(null);
            profile.setCourse(null);
            profile.setYearLevel(null);
            profile.setGradeLevel(null);
            profile.setSection(null);
        }
        profile.setRole(user.role());
        profile.setUpdatedAt(clock.instant());
        return profiles.save(profile);
    }

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

    @Transactional(readOnly = true)
    public List<UserProfile> search(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return profiles.search(text.trim().toLowerCase(Locale.ROOT), PageRequest.of(0, 8));
    }
}
