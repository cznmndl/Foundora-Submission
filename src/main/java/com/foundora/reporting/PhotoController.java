package com.foundora.reporting;

import com.foundora.auth.FoundoraUser;
import com.foundora.database.entity.Item;
import com.foundora.database.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

/**
 * Serves a report's photo from the private bucket, only to people allowed to see the report.
 * Photos the user may not see get the same 404 as photos that don't exist.
 */
@RestController
public class PhotoController {

    private static final Logger log = LoggerFactory.getLogger(PhotoController.class);

    private final ReportService reports;
    private final StorageService storage;

    public PhotoController(ReportService reports, StorageService storage) {
        this.reports = reports;
        this.storage = storage;
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user) {
        Item item = reports.findViewable(id, user).filter(Item::hasPhoto).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            StorageService.StoredPhoto photo = storage.downloadPhoto(item.getImagePath());
            return ResponseEntity.ok()
                    .contentType(photo.contentType())
                    // "private": browsers may cache it, shared proxies may not.
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .eTag(item.getImagePath())
                    .body(photo.bytes());
        } catch (RestClientException e) {
            log.warn("Could not load photo {} for report {}", item.getImagePath(), id, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
