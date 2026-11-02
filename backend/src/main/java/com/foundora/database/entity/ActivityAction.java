package com.foundora.database.entity;

import java.util.Arrays;
import java.util.List;

public enum ActivityAction {
    SUBMITTED("Submitted", Group.REPORTS, "Submitted"),
    APPROVED("Approved", Group.REPORTS, "Approved"),
    REJECTED("Rejected", Group.REPORTS, "Rejected"),
    CLAIMED("Marked as claimed", Group.CLAIMS, "Marked as claimed"),
    REMOVED("Removed", Group.REPORTS, "Removed"),
    COMMENT_DELETED("Deleted a comment", Group.REPORTS, "Deleted a comment"),
    CLAIM_DISPUTED("Marked claim as disputed", Group.CLAIMS, "Marked claim as disputed"),
    CLAIM_RESOLVED("Resolved claim dispute", Group.CLAIMS, "Resolved claim dispute"),
    CLAIM_REOPENED("Reopened claim", Group.CLAIMS, "Reopened claim"),
    ARCHIVED("Archived", Group.REPORTS, "Archived"),
    CLAIM_REQUESTED("Requested to claim", Group.CLAIM_REQUESTS, "Requested to claim"),
    CLAIM_INFO_REQUESTED("Asked the claimant for more information", Group.CLAIM_REQUESTS, "Asked for more information"),
    CLAIM_VERIFIED("Verified a claim request", Group.CLAIM_REQUESTS, "Verified"),
    CLAIM_DENIED("Denied a claim request", Group.CLAIM_REQUESTS, "Denied"),
    CLAIM_WITHDRAWN("Withdrew a claim request", Group.CLAIM_REQUESTS, "Withdrew"),
    CLAIM_PASS_EXTENDED("Extended a pickup pass", Group.CLAIM_REQUESTS, "Extended a pickup pass"),
    CLAIM_PASS_EXPIRED("Pickup pass expired", Group.CLAIM_REQUESTS, "Pickup pass expired"),
    HANDLED_BY_SDFO("Marked as handled by SDFO", Group.REPORTS, "Handled by SDFO"),
    PIN_RESET("Reset a PIN", Group.ACCOUNTS, "Reset PIN"),
    ACCOUNT_UNLOCKED("Unlocked an account", Group.ACCOUNTS, "Unlocked account");

    public enum Group {
        REPORTS("Reports"),
        CLAIMS("Claims"),
        CLAIM_REQUESTS("Claim requests"),
        ACCOUNTS("Accounts");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String slug() {
            return Slugs.of(this);
        }

        public List<ActivityAction> actions() {
            return Arrays.stream(ActivityAction.values()).filter(a -> a.group == this).toList();
        }

        public static Group fromSlug(String slug) {
            return Slugs.find(Group.class, slug);
        }
    }

    private final String label;
    private final Group group;
    private final String filterLabel;

    ActivityAction(String label, Group group, String filterLabel) {
        this.label = label;
        this.group = group;
        this.filterLabel = filterLabel;
    }

    public String label() {
        return label;
    }

    public Group group() {
        return group;
    }

    public String filterLabel() {
        return filterLabel;
    }

    public String slug() {
        return Slugs.of(this);
    }

    public static ActivityAction fromSlug(String slug) {
        return Slugs.find(ActivityAction.class, slug);
    }
}
