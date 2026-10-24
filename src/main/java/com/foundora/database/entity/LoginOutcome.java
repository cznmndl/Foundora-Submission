package com.foundora.database.entity;

public enum LoginOutcome {
    GOOGLE_OK,
    REJECTED_DOMAIN,
    INVALID_TOKEN,
    PIN_SET,
    PIN_SUCCESS,
    PIN_FAILED,
    LOCKED,
    PIN_RESET
}
