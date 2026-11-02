package com.foundora.auth;

import com.foundora.database.entity.LoginOutcome;

/**
 * Sign-in was refused. The message is safe to show to the user.
 */
public class LoginRejectedException extends RuntimeException {

    private final LoginOutcome outcome;
    private final String uid;
    private final String email;

    public LoginRejectedException(String message, LoginOutcome outcome) {
        this(message, outcome, null, null);
    }

    public LoginRejectedException(String message, LoginOutcome outcome, String uid, String email) {
        super(message);
        this.outcome = outcome;
        this.uid = uid;
        this.email = email;
    }

    public LoginOutcome outcome() {
        return outcome;
    }

    public String uid() {
        return uid;
    }

    public String email() {
        return email;
    }
}
