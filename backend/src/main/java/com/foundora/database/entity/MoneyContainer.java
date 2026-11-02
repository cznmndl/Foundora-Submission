package com.foundora.database.entity;

/** What found or lost money was in. Stored by name; only the reporter, the claimant and SDFO see it. */
public enum MoneyContainer {
    ENVELOPE("Envelope"),
    COIN_PURSE("Coin purse"),
    LOOSE("Loose"),
    OTHER("Other");

    private final String label;

    MoneyContainer(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
