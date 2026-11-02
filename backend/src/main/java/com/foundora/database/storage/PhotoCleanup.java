package com.foundora.database.storage;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

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
