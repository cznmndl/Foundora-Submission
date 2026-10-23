package com.foundora.database.entity;

import java.util.EnumSet;
import java.util.Set;

public enum ReportStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CLAIMED("Claimed / Returned"),
    REMOVED("Removed"),
    HANDLED("Handled by SDFO");

    public static final Set<ReportStatus> ON_FEED = EnumSet.of(APPROVED, CLAIMED);

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return Slugs.of(this);
    }

    public boolean canMoveTo(ReportStatus next) {
        return switch (this) {
            case PENDING -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == CLAIMED || next == REMOVED || next == HANDLED;
            case CLAIMED -> next == REMOVED || next == APPROVED;
            case REJECTED, REMOVED, HANDLED -> false;
        };
    }

    public static ReportStatus fromSlug(String slug) {
        return Slugs.find(ReportStatus.class, slug);
    }
}
