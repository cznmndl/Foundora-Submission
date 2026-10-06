package com.foundora.service;

/** A comment that can't be posted or deleted, with a message for the user. */
public class CommentException extends RuntimeException {
    public CommentException(String message) {
        super(message);
    }
}
