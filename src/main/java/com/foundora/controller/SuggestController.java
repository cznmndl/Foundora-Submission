package com.foundora.controller;

import com.foundora.feed.FeedIndex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Suggestions as you type, answered from the prefix tries in the feed index. */
@RestController
public class SuggestController {

    private final FeedIndex feed;

    public SuggestController(FeedIndex feed) {
        this.feed = feed;
    }

    @GetMapping("/api/suggest/locations")
    public List<String> locations(@RequestParam(defaultValue = "") String q) {
        return feed.suggestLocations(limit(q), 8);
    }

    @GetMapping("/api/suggest/search")
    public List<String> searchWords(@RequestParam(defaultValue = "") String q) {
        String prefix = limit(q);
        return prefix.isBlank() ? List.of() : feed.suggestSearchWords(prefix, 6);
    }

    private static String limit(String q) {
        return q.length() > 60 ? q.substring(0, 60) : q;
    }
}
