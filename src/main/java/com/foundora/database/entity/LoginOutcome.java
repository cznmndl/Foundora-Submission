package com.foundora.database.entity;

/** What happened at one sign-in step, stored with each {@link LoginAttempt}. */
public enum LoginOutcome {
    GOOGLE_OK,
    REJECTED_DOMAIN,
    INVALID_TOKEN,
    PIN_SET,
    PIN_SUCCESS,
    PIN_FAILED,
    LOCKED,
    PIN_RESET,
    ACCOUNT_UNLOCKED
}
