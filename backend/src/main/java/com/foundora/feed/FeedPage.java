package com.foundora.feed;

import java.util.List;

public record FeedPage(List<FeedPost> posts, int page, int totalMatches, boolean hasMore) {
}
