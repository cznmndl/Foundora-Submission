package com.foundora.model;

import java.util.Collection;

/**
 * The status people see: Under review → Posted → Claim pending → Verified → Returned. Derived from the
 * report's {@link ReportStatus} and its open claim requests, so the report lifecycle itself is unchanged.
 */
public enum ItemStage {
    UNDER_REVIEW("Under review"),
    POSTED("Posted"),
    CLAIM_PENDING("Claim pending"),
    VERIFIED("Verified"),
    RETURNED("Returned"),
    NOT_POSTED("Not posted"),
    REMOVED("Removed");

    private final String label;

    ItemStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }

    /** Position on the five-step track (1-5), or 0 for reports that left it (not posted, removed). */
    public int step() {
        return switch (this) {
            case UNDER_REVIEW -> 1;
            case POSTED -> 2;
            case CLAIM_PENDING -> 3;
            case VERIFIED -> 4;
            case RETURNED -> 5;
            case NOT_POSTED, REMOVED -> 0;
        };
    }

    /** The stage of a report whose open claim requests have these statuses. */
    public static ItemStage of(ReportStatus status, Collection<ClaimRequestStatus> openRequests) {
        return switch (status) {
            case PENDING -> UNDER_REVIEW;
            case REJECTED -> NOT_POSTED;
            case REMOVED -> REMOVED;
            case CLAIMED -> RETURNED;
            case APPROVED -> openRequests.contains(ClaimRequestStatus.VERIFIED) ? VERIFIED
                    : openRequests.isEmpty() ? POSTED : CLAIM_PENDING;
        };
    }

    public static ItemStage of(ReportStatus status) {
        return of(status, java.util.List.of());
    }
}
