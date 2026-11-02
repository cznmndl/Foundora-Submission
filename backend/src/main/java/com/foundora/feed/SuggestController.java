package com.foundora.feed;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
