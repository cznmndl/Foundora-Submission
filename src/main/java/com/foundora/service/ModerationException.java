package com.foundora.service;

/** A moderation action that doesn't fit the report's current status; the message is shown to the admin. */
public class ModerationException extends RuntimeException {

    public ModerationException(String message) {
        super(message);
    }
}
