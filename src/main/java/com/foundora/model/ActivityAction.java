package com.foundora.model;

public enum ActivityAction {
    SUBMITTED("Submitted"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CLAIMED("Marked as claimed"),
    REMOVED("Removed");

    private final String label;

    ActivityAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return name().toLowerCase();
    }

    public static ActivityAction fromSlug(String slug) {
        if (slug == null) {
            return null;
        }
        for (ActivityAction action : values()) {
            if (action.slug().equalsIgnoreCase(slug.trim())) {
                return action;
            }
        }
        return null;
    }
}
