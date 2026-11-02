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

    public String slug() {
        return Slugs.of(this);
    }

    public static ReportType fromSlug(String slug) {
        return Slugs.find(ReportType.class, slug);
    }
}
