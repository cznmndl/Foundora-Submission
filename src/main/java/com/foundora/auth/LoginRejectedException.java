package com.foundora.auth;

public class LoginRejectedException extends RuntimeException {

    public LoginRejectedException(String message) {
        super(message);
    }
}
