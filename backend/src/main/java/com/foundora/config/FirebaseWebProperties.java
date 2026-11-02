package com.foundora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("firebase.web")
public record FirebaseWebProperties(String apiKey, String authDomain, String projectId, String appId) {
}
