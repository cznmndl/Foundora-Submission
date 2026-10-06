package com.foundora.model;

public enum ActivityAction {
    SUBMITTED("Submitted"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CLAIMED("Marked as claimed"),
    REMOVED("Removed"),
    COMMENT_DELETED("Deleted a comment"),
    CLAIM_DISPUTED("Marked claim as disputed"),
    CLAIM_RESOLVED("Resolved claim dispute"),
    CLAIM_REOPENED("Reopened claim"),
    ARCHIVED("Archived");

    private final String label;

    ActivityAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return name().toLowerCase().replace('_', '-');
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
