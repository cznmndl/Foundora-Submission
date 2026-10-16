package com.foundora.service;

/**
 * What a claimant types about money they lost, as sent by the form (parsed and checked by
 * {@link ClaimRequestService}). Also handed back to the form as a draft when something needs fixing.
 *
 * @param lostOn   yyyy-MM-dd
 * @param lostTime HH:mm, optional
 */
public record MoneyClaimInput(String amount, String denominations, String where, String lostOn, String lostTime,
                              String container) {

    public static final MoneyClaimInput EMPTY = new MoneyClaimInput(null, null, null, null, null, null);
}
