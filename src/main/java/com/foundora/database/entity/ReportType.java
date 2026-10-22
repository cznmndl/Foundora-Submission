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
        return name().toLowerCase();
    }

    public static ReportType fromSlug(String slug) {
        if (slug == null) {
            return null;
        }
        for (ReportType type : values()) {
            if (type.slug().equalsIgnoreCase(slug.trim())) {
                return type;
            }
        }
        return null;
    }
}
