package com.foundora.database.entity;

public enum Category {
    ELECTRONICS("Electronics"),
    IDS_DOCUMENTS("IDs & Documents"),
    TUMBLERS_BOTTLES("Tumblers & Bottles"),
    UMBRELLAS("Umbrellas"),
    SCHOOL_SUPPLIES("School Supplies"),
    BAGS_WALLETS("Bags & Wallets"),
    CLOTHING_ACCESSORIES("Clothing & Accessories"),
    KEYS("Keys"),
    MONEY("Money"),
    OTHERS("Others");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String slug() {
        return Slugs.of(this);
    }

    public static Category fromSlug(String slug) {
        return Slugs.find(Category.class, slug);
    }
}
