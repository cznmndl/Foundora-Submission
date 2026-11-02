package com.foundora.database.entity;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimRequestStatus {
    PENDING("Waiting for review"),
    NEEDS_INFO("More information needed"),
    VERIFIED("Verified"),
    DENIED("Denied"),
    WITHDRAWN("Withdrawn"),
    EXPIRED("Pickup pass expired"),
    RELEASED("Returned to you"),
    CLOSED("Closed");

    public static final Set<ClaimRequestStatus> OPEN = EnumSet.of(PENDING, NEEDS_INFO, VERIFIED);

    private final String label;

    ClaimRequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return Slugs.of(this);
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
