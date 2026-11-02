package com.foundora.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One sign-in step in the login history. Created by db/migration/V16__login_attempts.sql. */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginOutcome outcome;

    private Integer attemptNumber;

    @Column(nullable = false)
    private Instant createdAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(String uid, String email, LoginOutcome outcome, Integer attemptNumber, Instant createdAt) {
        this.uid = uid;
        this.email = email;
        this.outcome = outcome;
        this.attemptNumber = attemptNumber;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public LoginOutcome getOutcome() {
        return outcome;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
