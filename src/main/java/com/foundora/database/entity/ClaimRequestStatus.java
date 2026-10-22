package com.foundora.database.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a claim request stands. PENDING and NEEDS_INFO wait for an admin; VERIFIED is a pickup pass that
 * expires; RELEASED means the item was handed to this requester. CLOSED requests ended because the item was
 * released to someone else, rejected or removed.
 */
public enum ClaimRequestStatus {
    PENDING("Waiting for review"),
    NEEDS_INFO("More information needed"),
    VERIFIED("Verified"),
    DENIED("Denied"),
    WITHDRAWN("Withdrawn"),
    EXPIRED("Pickup pass expired"),
    RELEASED("Returned to you"),
    CLOSED("Closed");

    /** Requests still in play: they keep the item "Claim pending" or "Verified". */
    public static final Set<ClaimRequestStatus> OPEN = EnumSet.of(PENDING, NEEDS_INFO, VERIFIED);

    private final String label;

    ClaimRequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
