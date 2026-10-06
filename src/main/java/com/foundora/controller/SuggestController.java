package com.foundora.controller;

import com.foundora.feed.FeedIndex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Search-as-you-type, answered from the prefix trie in the feed index. The trie holds words from item names
 * and descriptions only, never locations, so suggestions can't reveal where an item was lost or found.
 */
@RestController
public class SuggestController {

    private final FeedIndex feed;

    public SuggestController(FeedIndex feed) {
        this.feed = feed;
    }

    @GetMapping("/api/suggest/search")
    public List<String> searchWords(@RequestParam(defaultValue = "") String q) {
        String prefix = q.length() > 60 ? q.substring(0, 60) : q;
        return prefix.isBlank() ? List.of() : feed.suggestSearchWords(prefix, 6);
    }
}
