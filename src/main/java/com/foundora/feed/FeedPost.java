package com.foundora.feed;

import com.foundora.auth.FoundoraUser;
import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.ItemStage;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a feed card shows to everyone. Deliberately has no reporter email and no location: when an item is
 * claimed, SDFO asks the claimant where they lost it, so the location must stay secret. Public pages are
 * rendered from this record, so a template can't show either by mistake. Admins get both separately
 * ({@code AdminPostDetails}).
 *
 * @param claimedBy the claimant's name on a claimed post (students may see who claimed it); null otherwise
 * @param stage     what people see as its status: Posted, Claim pending, Verified, Returned… (never who asked)
 */
public record FeedPost(UUID id, ReportType type, Category category, String itemName, String description,
                       LocalDate dateLostFound, String reporterName, boolean hasPhoto,
                       ReportStatus status, Instant createdAt, String claimedBy, ItemStage stage) {

    public static FeedPost from(Item item) {
        return from(item, null, ItemStage.of(item.getStatus()));
    }

    public static FeedPost from(Item item, String claimedBy) {
        return from(item, claimedBy, ItemStage.of(item.getStatus()));
    }

    public static FeedPost from(Item item, String claimedBy, ItemStage stage) {
        return new FeedPost(item.getId(), item.getReportType(), item.getCategory(), item.getItemName(),
                item.getDescription(), item.getDateLostFound(), item.getReporterName(), item.hasPhoto(),
                item.getStatus(), item.getCreatedAt(),
                item.getStatus() == ReportStatus.CLAIMED ? claimedBy : null, stage);
    }

    public String reporterInitial() {
        return FoundoraUser.initialOf(reporterName);
    }

    public boolean claimed() {
        return status == ReportStatus.CLAIMED;
    }

    public boolean found() {
        return type == ReportType.FOUND;
    }
}
