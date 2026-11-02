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

@Service
@EnableConfigurationProperties(SupabaseProperties.class)
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    public static final String ITEMS = "items";
    public static final String COMMENTS = "comments";
    public static final String CLAIMS = "claims";

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

    public String storePhoto(byte[] jpeg) {
        return storePhoto(ITEMS, jpeg);
    }

    public static final String UPLOAD_FAILED = "We couldn't save the photo just now. Please try again in a moment.";

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

    public void deletePhotoQuietly(String path) {
        try {
            deletePhoto(path);
        } catch (RuntimeException e) {
            log.error("Could not delete photo {} from bucket {}; delete it manually", path, supabase.bucket(), e);
        }
    }

    private static String[] split(String path) {
        var matcher = PATH.matcher(path == null ? "" : path);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a photo path: " + path);
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    private static boolean isNotFound(HttpStatusCode status, String body) {
        return status.value() == 404 || (status.value() == 400 && body != null && body.contains("not_found"));
    }
}
