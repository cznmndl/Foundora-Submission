package com.foundora.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes a rejected or removed report's photo after the status change has committed. If Supabase is
 * unreachable, moderation still succeeds; the failure is logged with the photo's path.
 */
@Component
public class PhotoCleanup {

    private final StorageService storage;

    public PhotoCleanup(StorageService storage) {
        this.storage = storage;
    }

    @TransactionalEventListener
    public void onPhotoDetached(PhotoDeletionEvent event) {
        storage.deletePhotoQuietly(event.path());
    }
}
