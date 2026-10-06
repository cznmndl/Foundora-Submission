package com.foundora.service;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.SdfoOffice;
import com.foundora.model.ActivityAction;
import com.foundora.model.ActivityLogEntry;
import com.foundora.model.Comment;
import com.foundora.model.Item;
import com.foundora.model.UserProfile;
import com.foundora.repository.ActivityLogRepository;
import com.foundora.repository.CommentRepository;
import com.foundora.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Comments on posts. They appear at once (no approval). Their photos follow the report-photo rules:
 * re-encoded as JPEG (dropping GPS and other metadata), stored in the private bucket, served only through
 * the app, and deleted with the comment.
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final ItemRepository items;
    private final ActivityLogRepository activityLog;
    private final ProfileService profiles;
    private final ReportAccess access;
    private final ImageProcessor imageProcessor;
    private final StorageService storage;
    private final SdfoOffice sdfo;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration cooldown;

    public CommentService(CommentRepository comments, ItemRepository items, ActivityLogRepository activityLog,
                          ProfileService profiles, ReportAccess access, ImageProcessor imageProcessor,
                          StorageService storage, SdfoOffice sdfo, ApplicationEventPublisher events,
                          TransactionTemplate transaction, Clock clock,
                          @Value("${foundora.comments.cooldown:PT30S}") Duration cooldown) {
        this.comments = comments;
        this.items = items;
        this.activityLog = activityLog;
        this.profiles = profiles;
        this.access = access;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
        this.sdfo = sdfo;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
        this.cooldown = cooldown;
    }

    /** Comments are open on posts that are on the feed: Approved, or Claimed and not yet archived. */
    public boolean isOpen(Item item) {
        return item.isOnFeed();
    }

    /** All comments on the post, oldest first, as this viewer may see them. */
    @Transactional(readOnly = true)
    public List<CommentView> viewsFor(Item item, FoundoraUser viewer) {
        List<Comment> list = comments.findByItemIdOrderByCreatedAtAscIdAsc(item.getId());
        Map<String, UserProfile> authorProfiles = viewer.isAdmin()
                ? profiles.byEmails(list.stream().map(Comment::getAuthorEmail).toList())
                : Map.of();
        return list.stream().map(c -> {
            boolean admin = viewer.isAdmin();
            UserProfile profile = admin ? authorProfiles.get(c.getAuthorEmail().toLowerCase(Locale.ROOT)) : null;
            return new CommentView(c.getId(), c.getAuthorName(), badge(c), isOriginalPoster(item, c),
                    c.getBody(), c.hasPhoto(), c.getCreatedAt(), canDelete(c, viewer),
                    admin ? c.getAuthorEmail() : null,
                    admin ? (profile != null ? profile.summary() : "No profile yet") : null);
        }).toList();
    }

    /** Comment counts for a page of posts (0 for posts without comments). */
    @Transactional(readOnly = true)
    public Map<UUID, Long> counts(Collection<UUID> itemIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (Object[] row : comments.countByItemIds(itemIds)) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return counts;
    }

    /**
     * Posts a comment. Throws {@link CommentException} (closed post, empty, too long, too soon after the last
     * one) or {@link InvalidImageException} (unreadable or oversized photo) with a message for the user.
     */
    public Comment add(UUID itemId, FoundoraUser author, String text, MultipartFile photo) {
        Item item = items.findById(itemId).filter(i -> access.canView(i, author))
                .orElseThrow(() -> new CommentException("That post isn't available."));
        if (!isOpen(item)) {
            throw new CommentException("Comments are closed on this post.");
        }
        String body = text == null || text.isBlank() ? null : text.strip();
        boolean hasPhoto = photo != null && !photo.isEmpty();
        if (body == null && !hasPhoto) {
            throw new CommentException("Write a comment or add a photo.");
        }
        if (body != null && body.length() > Comment.MAX_BODY) {
            throw new CommentException("Keep your comment to " + Comment.MAX_BODY + " characters or fewer.");
        }
        Instant now = clock.instant();
        Optional<Comment> last = comments.findTopByAuthorEmailOrderByCreatedAtDesc(author.email());
        if (last.isPresent() && last.get().getCreatedAt().plus(cooldown).isAfter(now)) {
            long wait = Math.max(1, Duration.between(now, last.get().getCreatedAt().plus(cooldown)).toSeconds());
            throw new CommentException("You're commenting too fast. Please wait " + wait + " seconds and try again.");
        }

        String photoPath = hasPhoto ? storage.storePhoto(StorageService.COMMENTS, imageProcessor.toJpeg(photo)) : null;
        try {
            return transaction.execute(status -> comments.save(new Comment(item.getId(), author.uid(),
                    author.email(), author.name(), author.role(), body, photoPath, now)));
        } catch (RuntimeException e) {
            if (photoPath != null) {
                storage.deletePhotoQuietly(photoPath);
            }
            throw e;
        }
    }

    /**
     * Deletes a comment: its author may delete it, and admins may delete any comment. A deletion by an admin
     * is written to the activity log. The photo is deleted from storage after the deletion commits.
     */
    @Transactional
    public Item delete(UUID commentId, FoundoraUser user, String reason) {
        Comment comment = comments.findById(commentId)
                .orElseThrow(() -> new CommentException("That comment was already deleted."));
        if (!canDelete(comment, user)) {
            throw new CommentException("You can only delete your own comments.");
        }
        Item item = items.findById(comment.getItemId()).orElseThrow();
        comments.delete(comment);
        if (comment.hasPhoto()) {
            events.publishEvent(new PhotoDeletionEvent(comment.getImagePath()));
        }
        if (user.isAdmin()) {
            String trimmed = reason == null || reason.isBlank() ? null : reason.strip();
            String note = "Comment by " + comment.getAuthorName()
                    + (trimmed != null ? ". Reason: " + truncate(trimmed, Item.MAX_REASON) : "");
            activityLog.save(new ActivityLogEntry(item, ActivityAction.COMMENT_DELETED, user.email(), user.name(),
                    note, clock.instant()));
        }
        return item;
    }

    /** The comment if it has a photo and this user may see its post. */
    @Transactional(readOnly = true)
    public Optional<Comment> findPhotoViewable(UUID commentId, FoundoraUser user) {
        return comments.findById(commentId).filter(Comment::hasPhoto)
                .filter(c -> items.findById(c.getItemId()).map(i -> access.canView(i, user)).orElse(false));
    }

    private boolean canDelete(Comment comment, FoundoraUser user) {
        return user.isAdmin() || comment.getAuthorEmail().equalsIgnoreCase(user.email());
    }

    private String badge(Comment comment) {
        if (sdfo.isSdfoAccount(comment.getAuthorEmail())) {
            return "SDFO";
        }
        return comment.getAuthorRole().label();
    }

    private static boolean isOriginalPoster(Item item, Comment comment) {
        return item.getReporterEmail().equalsIgnoreCase(comment.getAuthorEmail());
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
