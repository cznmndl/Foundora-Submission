package com.foundora.service;

import com.foundora.auth.FoundoraUser;
import com.foundora.dsa.PendingQueue;
import com.foundora.feed.ItemChangedEvent;
import com.foundora.model.ActivityAction;
import com.foundora.model.ActivityLogEntry;
import com.foundora.model.ClaimRecord;
import com.foundora.model.ClaimStatus;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.model.UserProfile;
import com.foundora.repository.ActivityLogRepository;
import com.foundora.repository.ClaimRecordRepository;
import com.foundora.repository.ItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin actions on reports. Each action checks the status transition is allowed, updates the report and
 * writes the activity log in one transaction. Feed updates and photo deletion run after it commits.
 */
@Service
public class ModerationService {

    /** How far ahead of the server clock a release time may be (an admin's watch running a little fast). */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final ItemRepository items;
    private final ActivityLogRepository activityLog;
    private final ClaimRecordRepository claims;
    private final ProfileService profiles;
    private final ClaimRequestService claimRequests;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public record Overview(long pending, long approvedLost, long approvedFound, long claimed, long rejected,
                           long archived) {
    }

    public ModerationService(ItemRepository items, ActivityLogRepository activityLog, ClaimRecordRepository claims,
                             ProfileService profiles, ClaimRequestService claimRequests,
                             ApplicationEventPublisher events, Clock clock) {
        this.items = items;
        this.activityLog = activityLog;
        this.claims = claims;
        this.profiles = profiles;
        this.claimRequests = claimRequests;
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
                items.countByStatusAndArchivedAtIsNull(ReportStatus.CLAIMED),
                items.countByStatus(ReportStatus.REJECTED),
                items.countByArchivedAtIsNotNull());
    }

    @Transactional
    public Item approve(UUID id, FoundoraUser admin) {
        Item item = find(id);
        if (item.getStatus() != ReportStatus.PENDING) {
            // CLAIMED -> APPROVED is allowed only by reopening the claim, which records why.
            throw new ModerationException("\"" + item.getItemName() + "\" is " + item.getStatus().label()
                    + ", so it can't be approved.");
        }
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

    /**
     * Marks an approved report as claimed and saves the claim record: who it was released to, when, and by
     * which admin. The claimant is a registered user (picked by email) or typed in by hand. Open claim
     * requests end: the claimant's own becomes Released, everyone else's Closed.
     */
    @Transactional
    public Item markClaimed(UUID id, FoundoraUser admin, ClaimForm form) {
        Item item = find(id);
        if (!item.getStatus().canMoveTo(ReportStatus.CLAIMED)) {
            throw new ModerationException("\"" + item.getItemName() + "\" is " + item.getStatus().label()
                    + ", so it can't be marked as claimed.");
        }
        String uid = null;
        String email = null;
        String name = form.getClaimantName();
        String affiliation = form.getClaimantAffiliation();
        if (form.getClaimantEmail() != null) {
            UserProfile profile = profiles.byEmail(form.getClaimantEmail()).orElseThrow(() -> new ModerationException(
                    "No registered user has the email " + form.getClaimantEmail()
                            + ". Pick one from the suggestions, or leave it blank and type the name."));
            uid = profile.getUid();
            email = profile.getEmail();
            name = profile.getFullName();
            if (affiliation == null && !profile.summary().isBlank()) {
                affiliation = profile.summary();
            }
        }
        if (name == null) {
            throw new ModerationException("Enter the claimant's name, or pick a registered user.");
        }
        if (affiliation == null) {
            throw new ModerationException("Enter the claimant's course, year and section, or their department.");
        }
        if (name.length() > ClaimRecord.MAX_NAME || affiliation.length() > ClaimRecord.MAX_AFFILIATION) {
            throw new ModerationException("Keep the name and the course/department to "
                    + ClaimRecord.MAX_NAME + " characters or fewer.");
        }
        if (form.getNotes() != null && form.getNotes().length() > ClaimRecord.MAX_NOTES) {
            throw new ModerationException("Keep the notes to " + ClaimRecord.MAX_NOTES + " characters or fewer.");
        }
        Instant now = clock.instant();
        Instant releasedAt = releasedAt(form.getReleasedAt(), now);

        claims.save(new ClaimRecord(item.getId(), uid, email, name, affiliation, releasedAt, admin.email(),
                admin.name(), form.getNotes(), now));
        claimRequests.onReleased(item, email, now);
        return move(item, ReportStatus.CLAIMED, admin, "Released to " + name + " (" + affiliation + ")");
    }

    /** Flags the current claim as disputed (e.g. someone else says it's theirs). The item stays claimed. */
    @Transactional
    public ClaimRecord disputeClaim(UUID claimId, FoundoraUser admin, String note) {
        ClaimRecord claim = findClaim(claimId);
        if (claim.getStatus() != ClaimStatus.ACTIVE) {
            throw new ModerationException("This claim is " + claim.getStatus().label().toLowerCase()
                    + ", so it can't be marked as disputed.");
        }
        String text = requiredNote(note, "Describe the dispute, e.g. who else says it's theirs.");
        claim.changeStatus(ClaimStatus.DISPUTED, text, admin.name(), clock.instant());
        claims.save(claim);
        log(claim, ActivityAction.CLAIM_DISPUTED, admin, text);
        return claim;
    }

    /** Ends a dispute and keeps the claim as it was. */
    @Transactional
    public ClaimRecord resolveDispute(UUID claimId, FoundoraUser admin, String note) {
        ClaimRecord claim = findClaim(claimId);
        if (claim.getStatus() != ClaimStatus.DISPUTED) {
            throw new ModerationException("This claim isn't disputed.");
        }
        String text = note == null || note.isBlank() ? null : checkLength(note.strip());
        claim.changeStatus(ClaimStatus.ACTIVE, null, admin.name(), clock.instant());
        claims.save(claim);
        log(claim, ActivityAction.CLAIM_RESOLVED, admin, text);
        return claim;
    }

    /**
     * Reopens a claim (e.g. the item went to the wrong person): the claim record is kept, marked Reopened,
     * and the report goes back to Approved, on the feed and claimable again, even if it was archived.
     */
    @Transactional
    public Item reopenClaim(UUID claimId, FoundoraUser admin, String reason) {
        ClaimRecord claim = findClaim(claimId);
        if (!claim.isOpen()) {
            throw new ModerationException("This claim was already reopened.");
        }
        String text = requiredNote(reason, "Say why the claim is being reopened.");
        Item item = find(claim.getItemId());
        if (item.getStatus() != ReportStatus.CLAIMED) {
            throw new ModerationException("\"" + item.getItemName() + "\" is " + item.getStatus().label()
                    + ", so its claim can't be reopened.");
        }
        Instant now = clock.instant();
        claim.changeStatus(ClaimStatus.REOPENED, null, admin.name(), now);
        claims.save(claim);
        item.setStatus(ReportStatus.APPROVED);
        item.setClaimedAt(null);
        item.setArchivedAt(null);
        item.setUpdatedAt(now);
        items.save(item);
        activityLog.save(new ActivityLogEntry(item, ActivityAction.CLAIM_REOPENED, admin.email(), admin.name(),
                text, now));
        events.publishEvent(new ItemChangedEvent(item.getId()));
        return item;
    }

    @Transactional
    public Item remove(UUID id, FoundoraUser admin, String reason) {
        String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmed != null && trimmed.length() > Item.MAX_REASON) {
            throw new ModerationException("Keep the reason to " + Item.MAX_REASON + " characters or fewer.");
        }
        return move(id, ReportStatus.REMOVED, admin, trimmed);
    }

    private Item find(UUID id) {
        return items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private ClaimRecord findClaim(UUID id) {
        return claims.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    private void log(ClaimRecord claim, ActivityAction action, FoundoraUser admin, String note) {
        Item item = find(claim.getItemId());
        activityLog.save(new ActivityLogEntry(item, action, admin.email(), admin.name(), note, clock.instant()));
    }

    private Instant releasedAt(LocalDateTime local, Instant now) {
        if (local == null) {
            throw new ModerationException("Enter the date and time the item was released.");
        }
        Instant releasedAt = local.atZone(clock.getZone()).toInstant();
        if (releasedAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw new ModerationException("The release time can't be in the future.");
        }
        return releasedAt;
    }

    private static String requiredNote(String note, String missingMessage) {
        if (note == null || note.isBlank()) {
            throw new ModerationException(missingMessage);
        }
        return checkLength(note.strip());
    }

    private static String checkLength(String note) {
        if (note.length() > ClaimRecord.MAX_NOTES) {
            throw new ModerationException("Keep it to " + ClaimRecord.MAX_NOTES + " characters or fewer.");
        }
        return note;
    }

    private Item move(UUID id, ReportStatus next, FoundoraUser admin, String reason) {
        return move(find(id), next, admin, reason);
    }

    private Item move(Item item, ReportStatus next, FoundoraUser admin, String reason) {
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
        if (next == ReportStatus.REMOVED) {
            claimRequests.closeOpen(item, now);
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
