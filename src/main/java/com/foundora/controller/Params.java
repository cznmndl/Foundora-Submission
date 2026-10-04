package com.foundora.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Lenient parsing of query parameters: a malformed value is ignored rather than turned into an error page. */
final class Params {

    private Params() {
    }

    static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static int page(Integer value) {
        return value == null || value < 0 ? 0 : Math.min(value, 10_000);
    }

    static String text(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    /** The current path and query string, e.g. to come back here after an admin action. */
    static String currentUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null ? "" : "?" + query);
    }

    /** The current page's URL with its page parameter replaced. */
    static String withPage(HttpServletRequest request, int page) {
        return UriComponentsBuilder.fromPath(request.getRequestURI())
                .query(request.getQueryString())
                .replaceQueryParam("page", page)
                .build()
                .toUriString();
    }

    /** Only same-site paths, so a crafted returnTo can't send an admin to another website. */
    static String safeReturn(String returnTo, String fallback) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")
                || returnTo.contains("\\") || returnTo.contains("\r") || returnTo.contains("\n")) {
            return fallback;
        }
        return returnTo;
    }
}
