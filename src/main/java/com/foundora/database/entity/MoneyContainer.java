package com.foundora.database.entity;

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
