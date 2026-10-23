package com.foundora.database.entity;

import java.util.Collection;

public enum ItemStage {
    UNDER_REVIEW("Under review"),
    POSTED("Posted"),
    CLAIM_PENDING("Claim pending"),
    VERIFIED("Verified"),
    RETURNED("Returned"),
    NOT_POSTED("Not posted"),
    REMOVED("Removed"),
    HANDLED("Handled by SDFO");

    private final String label;

    ItemStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return Slugs.of(this);
    }

    public String icon() {
        return switch (this) {
            case UNDER_REVIEW -> "review";
            case POSTED -> "posted";
            case CLAIM_PENDING -> "clock";
            case VERIFIED -> "shield-check";
            case RETURNED -> "check";
            case NOT_POSTED, REMOVED -> "x";
            case HANDLED -> "shield";
        };
    }

    public int step() {
        return switch (this) {
            case UNDER_REVIEW -> 1;
            case POSTED -> 2;
            case CLAIM_PENDING -> 3;
            case VERIFIED -> 4;
            case RETURNED -> 5;
            case NOT_POSTED, REMOVED, HANDLED -> 0;
        };
    }

    public static ItemStage of(ReportStatus status, Collection<ClaimRequestStatus> openRequests) {
        return switch (status) {
            case PENDING -> UNDER_REVIEW;
            case REJECTED -> NOT_POSTED;
            case REMOVED -> REMOVED;
            case HANDLED -> HANDLED;
            case CLAIMED -> RETURNED;
            case APPROVED -> openRequests.contains(ClaimRequestStatus.VERIFIED) ? VERIFIED
                    : openRequests.isEmpty() ? POSTED : CLAIM_PENDING;
        };
    }

    public static ItemStage of(ReportStatus status) {
        return of(status, java.util.List.of());
    }
}
