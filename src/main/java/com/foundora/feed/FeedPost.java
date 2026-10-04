package com.foundora.feed;

import com.foundora.auth.FoundoraUser;
import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a feed card shows. Deliberately has no reporter email: public pages are rendered from this record,
 * so a template can't show the email by mistake.
 */
public record FeedPost(UUID id, ReportType type, Category category, String itemName, String description,
                       String location, LocalDate dateLostFound, String reporterName, boolean hasPhoto,
                       ReportStatus status, Instant createdAt) {

    public static FeedPost from(Item item) {
        return new FeedPost(item.getId(), item.getReportType(), item.getCategory(), item.getItemName(),
                item.getDescription(), item.getLocation(), item.getDateLostFound(), item.getReporterName(),
                item.hasPhoto(), item.getStatus(), item.getCreatedAt());
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
