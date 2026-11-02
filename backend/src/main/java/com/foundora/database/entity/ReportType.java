package com.foundora.database.entity;

public enum ReportType {
    LOST("Lost"),
    FOUND("Found");

    private final String label;

    ReportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Lower-case name used in URLs, e.g. {@code ?type=found}. */
    public String slug() {
        return Slugs.of(this);
    }

    public static ReportType fromSlug(String slug) {
        return Slugs.find(ReportType.class, slug);
    }
}
