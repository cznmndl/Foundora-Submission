package com.foundora.controller;

import com.foundora.feed.FeedQuery;
import com.foundora.model.Category;
import com.foundora.model.ReportType;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

/**
 * The feed's current filters, as read from the URL. Templates use it to highlight the active tab and to build
 * links that change one filter while keeping the rest.
 */
public final class FeedFilters {

    private final ReportType type;
    private final Category category;
    private final String text;
    private final LocalDate from;
    private final LocalDate to;
    private final boolean hideClaimed;
    private final boolean oldestFirst;

    FeedFilters(ReportType type, Category category, String text, LocalDate from, LocalDate to,
                boolean hideClaimed, boolean oldestFirst) {
        this.type = type;
        this.category = category;
        this.text = text;
        this.from = from;
        this.to = to;
        this.hideClaimed = hideClaimed;
        this.oldestFirst = oldestFirst;
    }

    static FeedFilters parse(String type, String category, String q, String from, String to,
                             String hideClaimed, String sort) {
        return new FeedFilters(ReportType.fromSlug(type), Category.fromSlug(category), Params.text(q, 100),
                Params.date(from), Params.date(to), "on".equals(hideClaimed) || "true".equals(hideClaimed),
                "oldest".equals(sort));
    }

    FeedQuery toQuery(int page) {
        return new FeedQuery(type, category, text, from, to, hideClaimed, oldestFirst, page);
    }

    public ReportType type() {
        return type;
    }

    public Category category() {
        return category;
    }

    public String text() {
        return text;
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    public boolean hideClaimed() {
        return hideClaimed;
    }

    public boolean oldestFirst() {
        return oldestFirst;
    }

    public boolean isFiltered() {
        return toQuery(0).isFiltered();
    }

    /** Feed link with the type tab changed ("all", "lost" or "found"). */
    public String typeUrl(String slug) {
        return url("/", ReportType.fromSlug(slug), category, 0);
    }

    /** Feed link with the category changed; null or "all" clears it. */
    public String categoryUrl(String slug) {
        return url("/", type, Category.fromSlug(slug), 0);
    }

    /** Link that loads the given page of posts as an HTML fragment (for "Load more"). */
    public String moreUrl(int page) {
        return url("/feed/posts", type, category, page);
    }

    /** The same page without JavaScript: a full feed page showing that page of posts. */
    public String pageUrl(int page) {
        return url("/", type, category, page);
    }

    /** Short description of the filters for the empty state, e.g. "found items in Umbrellas matching “blue”". */
    public String describe() {
        StringBuilder words = new StringBuilder(type == null ? "items" : type.label().toLowerCase() + " items");
        if (category != null) {
            words.append(" in ").append(category.label());
        }
        if (text != null) {
            words.append(" matching “").append(text).append('”');
        }
        if (from != null || to != null) {
            words.append(" in that date range");
        }
        return words.toString();
    }

    private String url(String path, ReportType withType, Category withCategory, int page) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        if (withType != null) {
            b.queryParam("type", withType.slug());
        }
        if (withCategory != null) {
            b.queryParam("category", withCategory.slug());
        }
        if (text != null) {
            b.queryParam("q", text);
        }
        if (from != null) {
            b.queryParam("from", from);
        }
        if (to != null) {
            b.queryParam("to", to);
        }
        if (hideClaimed) {
            b.queryParam("hideClaimed", "on");
        }
        if (oldestFirst) {
            b.queryParam("sort", "oldest");
        }
        if (page > 0) {
            b.queryParam("page", page);
        }
        return b.encode().build().toUriString();
    }
}
