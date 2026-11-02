package com.foundora.feed;

import com.foundora.database.entity.Category;
import com.foundora.database.entity.ReportType;

import java.time.LocalDate;

public record FeedQuery(ReportType type, Category category, String text, LocalDate from, LocalDate to,
                        boolean hideClaimed, boolean oldestFirst, int page, String location,
                        boolean claimedOnly) {

    public static final int PAGE_SIZE = 10;

    public FeedQuery(ReportType type, Category category, String text, LocalDate from, LocalDate to,
                     boolean hideClaimed, boolean oldestFirst, int page) {
        this(type, category, text, from, to, hideClaimed, oldestFirst, page, null, false);
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public boolean isFiltered() {
        return type != null || category != null || hasText() || from != null || to != null || hideClaimed
                || location != null || claimedOnly;
    }
}
