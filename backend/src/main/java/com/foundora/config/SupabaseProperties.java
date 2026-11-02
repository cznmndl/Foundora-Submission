package com.foundora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supabase project URL, server-only secret key, and the public bucket for item photos.
 * Values come from SUPABASE_URL / SUPABASE_SECRET_KEY; never expose the secret key to the browser.
 */
@ConfigurationProperties("foundora.supabase")
public record SupabaseProperties(String url, String secretKey, String bucket) {
}
