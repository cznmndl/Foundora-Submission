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

/**
 * What a feed card shows to everyone. Deliberately has no reporter email, no location, no private details and
 * no claimant: when an item is claimed, SDFO asks the claimant where they lost it and what marks it has, so
 * those must stay secret, and who it was returned to is SDFO's record, not public. Public pages are rendered
 * from this record, so a template can't show any of them by mistake. Admins get them separately
 * ({@code AdminPostDetails}).
 *
 * <p>Money is stricter still: its card is only "Money" and when it was surrendered. No amount, description,
 * photo, finder or date found ever gets into this record, so no page or search index can show them.
 *
 * @param dateLostFound null for money (when it was found is one of the questions claimants must answer)
 * @param reporterName  null for money
 * @param stage         what people see as its status: Posted, Claim pending, Verified, Returned… (never who asked)
 * @param surrenderedAt money only: when SDFO took found money in (the approval), or when lost money was reported
 */
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

    /**
     * Found money is surrendered when SDFO approves its report (admins approve it once the cash is at the
     * office); until then, and for lost money, it's when the report was sent.
     */
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
