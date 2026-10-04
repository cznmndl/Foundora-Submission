package com.foundora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Users sign in with Firebase only, so Spring's default in-memory password user is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FoundoraApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoundoraApplication.class, args);
    }
}
