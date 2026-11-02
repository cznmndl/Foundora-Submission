package com.foundora.feed;

import com.foundora.auth.FoundoraUser;
import com.foundora.database.entity.Category;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ItemStage;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FeedPost(UUID id, ReportType type, Category category, String itemName, String description,
                       LocalDate dateLostFound, String reporterName, boolean hasPhoto,
                       ReportStatus status, Instant createdAt, ItemStage stage, boolean money,
                       Instant surrenderedAt) {

    public static FeedPost from(Item item) {
        return from(item, ItemStage.of(item.getStatus()));
    }

    public static FeedPost from(Item item, ItemStage stage) {
        if (item.isMoney()) {
            return new FeedPost(item.getId(), item.getReportType(), item.getCategory(), Item.MONEY_NAME, "",
                    null, null, false, item.getStatus(), item.getCreatedAt(), stage, true, surrenderedAt(item));
        }
        return new FeedPost(item.getId(), item.getReportType(), item.getCategory(), item.getItemName(),
                item.getDescription(), item.getDateLostFound(), item.getReporterName(), item.hasPhoto(),
                item.getStatus(), item.getCreatedAt(), stage, false, null);
    }

    private static Instant surrenderedAt(Item item) {
        boolean received = item.getReportType() == ReportType.FOUND && item.getStatus() != ReportStatus.PENDING
                && item.getReviewedAt() != null;
        return received ? item.getReviewedAt() : item.getCreatedAt();
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
