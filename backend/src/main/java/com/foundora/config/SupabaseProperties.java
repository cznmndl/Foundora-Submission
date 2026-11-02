package com.foundora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("foundora.supabase")
public record SupabaseProperties(String url, String secretKey, String bucket) {
}
