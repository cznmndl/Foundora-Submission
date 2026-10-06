package com.foundora.model;

/** Where a claim record stands. ACTIVE and DISPUTED claims keep the item claimed; REOPENED puts it back on the feed. */
public enum ClaimStatus {
    ACTIVE("Released"),
    DISPUTED("Disputed"),
    REOPENED("Reopened");

    private final String label;

    ClaimStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
