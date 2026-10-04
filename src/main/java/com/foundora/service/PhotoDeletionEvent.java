package com.foundora.service;

/** A photo to delete from storage once the transaction that detached it has committed. */
public record PhotoDeletionEvent(String path) {
}
