package com.foundora.feed;

import com.foundora.model.Category;
import com.foundora.model.ReportType;

import java.time.LocalDate;

/** Filters from the feed's search bar, tabs and sidebar. Null fields mean "don't filter". */
public record FeedQuery(ReportType type, Category category, String text, LocalDate from, LocalDate to,
                        boolean hideClaimed, boolean oldestFirst, int page) {

    public static final int PAGE_SIZE = 10;

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public boolean isFiltered() {
        return type != null || category != null || hasText() || from != null || to != null || hideClaimed;
    }
}
