package com.foundora.database.entity;

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
