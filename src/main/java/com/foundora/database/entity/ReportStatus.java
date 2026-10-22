package com.foundora.database.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a report is in its lifecycle. Only admins move a report between statuses:
 * PENDING -> APPROVED or REJECTED; APPROVED -> CLAIMED or REMOVED; CLAIMED -> REMOVED, or back to
 * APPROVED when an admin reopens the claim (e.g. it went to the wrong person). Found money that nobody claims
 * goes APPROVED -> HANDLED when SDFO deals with it under its own policy; like REMOVED, that's final.
 */
public enum ReportStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CLAIMED("Claimed / Returned"),
    REMOVED("Removed"),
    HANDLED("Handled by SDFO");

    /** Statuses that appear on the public feed (claimed ones only until they're archived). */
    public static final Set<ReportStatus> ON_FEED = EnumSet.of(APPROVED, CLAIMED);

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return name().toLowerCase();
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
        if (slug == null) {
            return null;
        }
        for (ReportStatus status : values()) {
            if (status.slug().equalsIgnoreCase(slug.trim())) {
                return status;
            }
        }
        return null;
    }
}
