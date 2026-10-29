package com.foundora.reporting;

/** An uploaded photo was refused; the message is safe to show to the user. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }

    public InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
