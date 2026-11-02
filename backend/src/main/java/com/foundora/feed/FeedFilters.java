package com.foundora.feed;

import com.foundora.config.Params;
import com.foundora.database.entity.Category;
import com.foundora.database.entity.ReportType;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

public final class FeedFilters {

    private final ReportType type;
    private final Category category;
    private final String text;
    private final LocalDate from;
    private final LocalDate to;
    private final boolean hideClaimed;
    private final boolean oldestFirst;
    private final boolean claimedOnly;
    private final String location;

    FeedFilters(ReportType type, Category category, String text, LocalDate from, LocalDate to,
                boolean hideClaimed, boolean oldestFirst, boolean claimedOnly, String location) {
        this.type = type;
        this.category = category;
        this.text = text;
        this.from = from;
        this.to = to;
        this.hideClaimed = hideClaimed;
        this.oldestFirst = oldestFirst;
        this.claimedOnly = claimedOnly;
        this.location = location;
    }

    static FeedFilters parse(String type, String category, String q, String from, String to,
                             String hideClaimed, String sort, String show, String location) {
        boolean claimedOnly = "claimed".equals(show);
        return new FeedFilters(claimedOnly ? null : ReportType.fromSlug(type), Category.fromSlug(category),
                Params.text(q, 100), Params.date(from), Params.date(to),
                !claimedOnly && ("on".equals(hideClaimed) || "true".equals(hideClaimed)),
                "oldest".equals(sort), claimedOnly, location);
    }

    FeedQuery toQuery(int page) {
        return new FeedQuery(type, category, text, from, to, hideClaimed, oldestFirst, page, location, claimedOnly);
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

    public boolean claimedOnly() {
        return claimedOnly;
    }

    public String location() {
        return location;
    }

    public boolean isFiltered() {
        return toQuery(0).isFiltered();
    }

    public String typeUrl(String slug) {
        return url("/", ReportType.fromSlug(slug), category, "claimed".equals(slug), 0);
    }

    public String categoryUrl(String slug) {
        return url("/", type, Category.fromSlug(slug), claimedOnly, 0);
    }

    public String moreUrl(int page) {
        return url("/feed/posts", type, category, claimedOnly, page);
    }

    public String pageUrl(int page) {
        return url("/", type, category, claimedOnly, page);
    }

    public String describe() {
        StringBuilder words = new StringBuilder(claimedOnly ? "returned items"
                : type == null ? "items" : type.label().toLowerCase() + " items");
        if (category != null) {
            words.append(" in ").append(category.label());
        }
        if (location != null) {
            words.append(" at ").append(location);
        }
        if (text != null) {
            words.append(" matching “").append(text).append('”');
        }
        if (from != null || to != null) {
            words.append(" in that date range");
        }
        return words.toString();
    }

    private String url(String path, ReportType withType, Category withCategory, boolean withClaimedOnly, int page) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        if (withClaimedOnly) {
            b.queryParam("show", "claimed");
        } else if (withType != null) {
            b.queryParam("type", withType.slug());
        }
        if (withCategory != null) {
            b.queryParam("category", withCategory.slug());
        }
        if (location != null) {
            b.queryParam("location", location);
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
        if (hideClaimed && !withClaimedOnly) {
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
