package com.foundora.database.entity;

/**
 * The fixed list of report categories. Stored in the database by name (e.g. IDS_DOCUMENTS).
 * {@link #MONEY} has its own privacy rules: see {@link Item#isMoney()}.
 */
public enum Category {
    ELECTRONICS("Electronics"),
    IDS_DOCUMENTS("IDs & Documents"),
    TUMBLERS_BOTTLES("Tumblers & Bottles"),
    UMBRELLAS("Umbrellas"),
    SCHOOL_SUPPLIES("School Supplies"),
    BAGS_WALLETS("Bags & Wallets"),
    CLOTHING_ACCESSORIES("Clothing & Accessories"),
    KEYS("Keys"),
    /** Loose cash, envelopes and coin purses. Wallets stay under {@link #BAGS_WALLETS}. */
    MONEY("Money"),
    OTHERS("Others");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** URL form, e.g. {@code ids-documents}. */
    public String slug() {
        return Slugs.of(this);
    }

    public static Category fromSlug(String slug) {
        return Slugs.find(Category.class, slug);
    }
}
