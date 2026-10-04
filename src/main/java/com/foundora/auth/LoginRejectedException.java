package com.foundora.auth;

/**
 * Sign-in was refused. The message is safe to show to the user.
 */
public class LoginRejectedException extends RuntimeException {

    public LoginRejectedException(String message) {
        super(message);
    }
}
