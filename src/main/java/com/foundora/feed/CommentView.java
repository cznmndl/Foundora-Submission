package com.foundora.feed;


import com.foundora.auth.FoundoraUser;

import java.time.Instant;
import java.util.UUID;

/**
 * One comment as a page shows it. {@code authorEmail} and {@code authorDetails} are filled in only when an
 * admin is viewing; for everyone else they're null, so a template can't show them by mistake.
 *
 * @param badge "Student", "Staff", "Admin" or "SDFO"
 */
public record CommentView(UUID id, String authorName, String badge, boolean originalPoster, String body,
                          boolean hasPhoto, Instant createdAt, boolean canDelete,
                          String authorEmail, String authorDetails) {

    public String authorInitial() {
        return FoundoraUser.initialOf(authorName);
    }

    public String badgeClass() {
        return "badge-role-" + badge.toLowerCase(java.util.Locale.ROOT);
    }
}
