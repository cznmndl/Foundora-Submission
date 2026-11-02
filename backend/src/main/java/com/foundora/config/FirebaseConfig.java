package com.foundora.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@EnableConfigurationProperties(FirebaseWebProperties.class)
public class FirebaseConfig {

    /** Filesystem path to the service account key; blank means use the classpath copy (local dev). */
    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    @Value("${firebase.project.id}")
    private String projectId;

    @Bean
    public FirebaseAuth firebaseAuth() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            Resource serviceAccount = StringUtils.hasText(credentialsPath)
                    ? new FileSystemResource(credentialsPath)
                    : new ClassPathResource("firebase-service-account.json");
            try (InputStream in = serviceAccount.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .setProjectId(projectId)
                        .build();
                FirebaseApp.initializeApp(options);
            }
        }

        return FirebaseAuth.getInstance();
    }
}
