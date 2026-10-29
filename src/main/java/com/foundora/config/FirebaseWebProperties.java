package com.foundora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public Firebase web config rendered into the sign-in page. Analytics is intentionally not configured.
 */
@ConfigurationProperties("firebase.web")
public record FirebaseWebProperties(String apiKey, String authDomain, String projectId, String appId) {
}
