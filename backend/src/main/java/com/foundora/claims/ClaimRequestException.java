package com.foundora.claims;

/** A claim request action that isn't allowed right now; the message is shown to the person. */
public class ClaimRequestException extends RuntimeException {

    public ClaimRequestException(String message) {
        super(message);
    }
}
