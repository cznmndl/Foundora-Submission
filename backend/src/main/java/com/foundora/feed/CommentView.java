package com.foundora.feed;

import com.foundora.auth.FoundoraUser;

import java.time.Instant;
import java.util.UUID;

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
