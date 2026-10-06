package com.foundora.service;

import com.foundora.model.Category;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;

import java.time.LocalDate;

/**
 * Filters on the admin's All Reports and Archive pages. Null means "any".
 *
 * @param location a campus place (exact match)
 * @param archived true: archived only (the Archive page); false: not archived; null: either
 */
public record AdminReportFilter(String text, ReportStatus status, ReportType type, Category category,
                                LocalDate from, LocalDate to, String location, Boolean archived) {

    public AdminReportFilter(String text, ReportStatus status, ReportType type, Category category,
                             LocalDate from, LocalDate to) {
        this(text, status, type, category, from, to, null, null);
    }
}
