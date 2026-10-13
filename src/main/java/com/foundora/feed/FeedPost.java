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
 * What a feed card shows to everyone. Deliberately has no reporter email, no location, no private details and
 * no claimant: when an item is claimed, SDFO asks the claimant where they lost it and what marks it has, so
 * those must stay secret, and who it was returned to is SDFO's record, not public. Public pages are rendered
 * from this record, so a template can't show any of them by mistake. Admins get them separately
 * ({@code AdminPostDetails}).
 *
 * @param stage what people see as its status: Posted, Claim pending, Verified, Returned… (never who asked)
 */
public record FeedPost(UUID id, ReportType type, Category category, String itemName, String description,
                       LocalDate dateLostFound, String reporterName, boolean hasPhoto,
                       ReportStatus status, Instant createdAt, ItemStage stage) {

    public static FeedPost from(Item item) {
        return from(item, ItemStage.of(item.getStatus()));
    }

    public static FeedPost from(Item item, ItemStage stage) {
        return new FeedPost(item.getId(), item.getReportType(), item.getCategory(), item.getItemName(),
                item.getDescription(), item.getDateLostFound(), item.getReporterName(), item.hasPhoto(),
                item.getStatus(), item.getCreatedAt(), stage);
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
