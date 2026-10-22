package com.foundora.database.storage;

import com.foundora.config.SupabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores report and comment photos in the private Supabase Storage bucket, using the server-only secret key.
 * Report photos live under items/, comment photos under comments/. Photos are never linked directly;
 * {@code PhotoController} serves them after checking who is asking.
 */
@Service
@EnableConfigurationProperties(SupabaseProperties.class)
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    public static final String ITEMS = "items";
    public static final String COMMENTS = "comments";
    public static final String CLAIMS = "claims";

    /** Photo paths look like items/&lt;file&gt;, comments/&lt;file&gt; or claims/&lt;file&gt;; anything else is refused before it reaches Supabase. */
    private static final Pattern PATH =
            Pattern.compile("(items|comments|claims)/([A-Za-z0-9-]+\\.(jpg|jpeg|png|webp|gif))");

    private final RestClient restClient;
    private final SupabaseProperties supabase;

    public record StoredPhoto(byte[] bytes, MediaType contentType) {
    }

    public StorageService(RestClient.Builder restClientBuilder, SupabaseProperties supabase) {
        this.supabase = supabase;
        this.restClient = restClientBuilder
                .baseUrl(supabase.url() + "/storage/v1")
                .defaultHeader("apikey", supabase.secretKey())
                .defaultHeader("Authorization", "Bearer " + supabase.secretKey())
                .build();
    }

    /** Uploads a report photo (JPEG bytes) under a new random name and returns the object path to save. */
    public String storePhoto(byte[] jpeg) {
        return storePhoto(ITEMS, jpeg);
    }

    /** Uploads JPEG bytes into {@code folder} (items, comments or claims) under a new random name; returns the object path. */
    public String storePhoto(String folder, byte[] jpeg) {
        if (!ITEMS.equals(folder) && !COMMENTS.equals(folder) && !CLAIMS.equals(folder)) {
            throw new IllegalArgumentException("Unknown photo folder: " + folder);
        }
        String fileName = UUID.randomUUID() + ".jpg";
        restClient.post()
                .uri("/object/{bucket}/{folder}/{fileName}", supabase.bucket(), folder, fileName)
                .contentType(MediaType.IMAGE_JPEG)
                .header("x-upsert", "false")
                .body(jpeg)
                .retrieve()
                .toBodilessEntity();
        return folder + "/" + fileName;
    }

    public StoredPhoto downloadPhoto(String path) {
        String[] parts = split(path);
        ResponseEntity<byte[]> response = restClient.get()
                .uri("/object/{bucket}/{folder}/{fileName}", supabase.bucket(), parts[0], parts[1])
                .retrieve()
                .toEntity(byte[].class);
        MediaType type = response.getHeaders().getContentType();
        return new StoredPhoto(response.getBody(), type != null ? type : MediaType.IMAGE_JPEG);
    }

    /** Deletes a photo. A photo that is already gone counts as deleted. */
    public void deletePhoto(String path) {
        String[] parts = split(path);
        try {
            restClient.delete()
                    .uri("/object/{bucket}/{folder}/{fileName}", supabase.bucket(), parts[0], parts[1])
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (!isNotFound(e.getStatusCode(), e.getResponseBodyAsString())) {
                throw e;
            }
        }
    }

    /** Best-effort delete for cleanup paths; failures are logged with the path so it can be removed by hand. */
    public void deletePhotoQuietly(String path) {
        try {
            deletePhoto(path);
        } catch (RuntimeException e) {
            log.error("Could not delete photo {} from bucket {}; delete it manually", path, supabase.bucket(), e);
        }
    }

    /** [folder, file name] of a valid photo path. */
    private static String[] split(String path) {
        var matcher = PATH.matcher(path == null ? "" : path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a photo path: " + path);
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    // Supabase Storage has answered a missing object with 404, and with 400 + "not_found" in the body.
    private static boolean isNotFound(HttpStatusCode status, String body) {
        return status.value() == 404 || (status.value() == 400 && body != null && body.contains("not_found"));
    }
}
