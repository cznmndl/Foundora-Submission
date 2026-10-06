package com.foundora.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness check for uptime monitors: plain "OK" with no sign-in needed. It says only that the app is up
 * and serving requests; it doesn't touch the database or reveal anything about the app.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .cacheControl(CacheControl.noStore())
                .body("OK");
    }
}
