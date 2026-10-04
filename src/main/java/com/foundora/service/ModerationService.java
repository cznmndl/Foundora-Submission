package com.foundora.service;

import com.foundora.auth.FoundoraUser;
import com.foundora.dsa.PendingQueue;
import com.foundora.feed.ItemChangedEvent;
import com.foundora.model.ActivityAction;
import com.foundora.model.ActivityLogEntry;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.repository.ActivityLogRepository;
import com.foundora.repository.ItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin actions on reports. Each action checks the status transition is allowed, updates the report and
 * writes the activity log in one transaction. Feed updates and photo deletion run after it commits.
 */
@Service
public class ModerationService {

    private final ItemRepository items;
    private final ActivityLogRepository activityLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public record Overview(long pending, long approvedLost, long approvedFound, long claimed, long rejected) {
    }

    public ModerationService(ItemRepository items, ActivityLogRepository activityLog,
                             ApplicationEventPublisher events, Clock clock) {
        this.items = items;
        this.activityLog = activityLog;
        this.events = events;
        this.clock = clock;
    }

    /** Pending reports as a FIFO queue: the oldest submission is reviewed first. */
    @Transactional(readOnly = true)
    public PendingQueue<Item> pendingQueue() {
        return PendingQueue.ofOldestFirst(items.findByStatusOrderByCreatedAtAsc(ReportStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(
                items.countByStatus(ReportStatus.PENDING),
                items.countByStatusAndReportType(ReportStatus.APPROVED, ReportType.LOST),
                items.countByStatusAndReportType(ReportStatus.APPROVED, ReportType.FOUND),
                items.countByStatus(ReportStatus.CLAIMED),
                items.countByStatus(ReportStatus.REJECTED));
    }

    @Transactional
    public Item approve(UUID id, FoundoraUser admin) {
        return move(id, ReportStatus.APPROVED, admin, null);
    }

    @Transactional
    public Item reject(UUID id, FoundoraUser admin, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new ModerationException("Enter a reason so the reporter knows why it was rejected.");
        }
        if (trimmed.length() > Item.MAX_REASON) {
            throw new ModerationException("Keep the reason to " + Item.MAX_REASON + " characters or fewer.");
        }
        return move(id, ReportStatus.REJECTED, admin, trimmed);
    }

    @Transactional
    public Item markClaimed(UUID id, FoundoraUser admin) {
        return move(id, ReportStatus.CLAIMED, admin, null);
    }

    @Transactional
    public Item remove(UUID id, FoundoraUser admin, String reason) {
        String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmed != null && trimmed.length() > Item.MAX_REASON) {
            throw new ModerationException("Keep the reason to " + Item.MAX_REASON + " characters or fewer.");
        }
        return move(id, ReportStatus.REMOVED, admin, trimmed);
    }

    private Item move(UUID id, ReportStatus next, FoundoraUser admin, String reason) {
        Item item = items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (!item.getStatus().canMoveTo(next)) {
            throw new ModerationException("\"" + item.getItemName() + "\" is " + item.getStatus().label()
                    + ", so it can't be " + verb(next) + ".");
        }

        Instant now = clock.instant();
        item.setStatus(next);
        item.setUpdatedAt(now);
        if (next == ReportStatus.APPROVED || next == ReportStatus.REJECTED) {
            item.setReviewedByEmail(admin.email());
            item.setReviewedByName(admin.name());
            item.setReviewedAt(now);
            item.setReviewReason(reason);
        }
        if (next == ReportStatus.CLAIMED) {
            item.setClaimedAt(now);
        }
        if ((next == ReportStatus.REJECTED || next == ReportStatus.REMOVED) && item.hasPhoto()) {
            // Detach now so the photo stops being served at once; the file is deleted after commit.
            events.publishEvent(new PhotoDeletionEvent(item.getImagePath()));
            item.setImagePath(null);
        }

        items.save(item);
        activityLog.save(new ActivityLogEntry(item, action(next), admin.email(), admin.name(), reason, now));
        events.publishEvent(new ItemChangedEvent(item.getId()));
        return item;
    }

    private static ActivityAction action(ReportStatus status) {
        return switch (status) {
            case APPROVED -> ActivityAction.APPROVED;
            case REJECTED -> ActivityAction.REJECTED;
            case CLAIMED -> ActivityAction.CLAIMED;
            case REMOVED -> ActivityAction.REMOVED;
            case PENDING -> throw new IllegalArgumentException("No action moves a report back to pending");
        };
    }

    private static String verb(ReportStatus status) {
        return switch (status) {
            case APPROVED -> "approved";
            case REJECTED -> "rejected";
            case CLAIMED -> "marked as claimed";
            case REMOVED -> "removed";
            case PENDING -> "reopened";
        };
    }
}
