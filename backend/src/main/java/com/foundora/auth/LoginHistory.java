package com.foundora.auth;

import com.foundora.database.entity.LoginAttempt;
import com.foundora.database.entity.LoginOutcome;
import com.foundora.database.repository.LoginAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class LoginHistory {

    private final LoginAttemptRepository attempts;
    private final Clock clock;

    public LoginHistory(LoginAttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional
    public void record(LoginOutcome outcome, String uid, String email, Integer attemptNumber) {
        attempts.save(new LoginAttempt(uid, email, outcome, attemptNumber, clock.instant()));
    }

    public void record(LoginOutcome outcome, FoundoraUser user, Integer attemptNumber) {
        record(outcome, user.uid(), user.email(), attemptNumber);
    }
}
