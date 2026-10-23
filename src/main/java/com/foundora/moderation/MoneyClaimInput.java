package com.foundora.moderation;

public record MoneyClaimInput(String amount, String denominations, String where, String lostOn, String lostTime,
                              String container) {

    public static final MoneyClaimInput EMPTY = new MoneyClaimInput(null, null, null, null, null, null);
}
