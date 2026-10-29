package com.foundora.feed;

import java.util.UUID;

/** Published when a report's status changes, so the feed index can update after the transaction commits. */
public record ItemChangedEvent(UUID itemId) {
}
