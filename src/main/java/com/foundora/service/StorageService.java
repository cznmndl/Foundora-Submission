package com.foundora.service;

import com.foundora.config.SupabaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Uploads item photos to the public Supabase Storage bucket from the server, using the secret key. */
@Service
@EnableConfigurationProperties(SupabaseProperties.class)
public class StorageService {

    // The stored content type and extension come from this list, never from the client's filename,
    // so a file can't be served from the public bucket as anything other than an image.
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private final RestClient restClient;
    private final SupabaseProperties supabase;

    public StorageService(RestClient.Builder restClientBuilder, SupabaseProperties supabase) {
        this.supabase = supabase;
        this.restClient = restClientBuilder
                .baseUrl(supabase.url() + "/storage/v1")
                .defaultHeader("apikey", supabase.secretKey())
                .defaultHeader("Authorization", "Bearer " + supabase.secretKey())
                .build();
    }

    /** Returns the public URL of the uploaded image, or null if no file was chosen. */
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be a JPEG, PNG, WebP or GIF image.");
        }

        String fileName = UUID.randomUUID() + "." + extension;
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded photo.", e);
        }

        restClient.post()
                .uri("/object/{bucket}/items/{fileName}", supabase.bucket(), fileName)
                .contentType(MediaType.parseMediaType(contentType))
                .header("x-upsert", "false")
                .body(bytes)
                .retrieve()
                .toBodilessEntity();

        return supabase.url() + "/storage/v1/object/public/" + supabase.bucket() + "/items/" + fileName;
    }
}
