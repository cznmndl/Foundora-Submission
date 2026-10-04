package com.foundora.service;

import com.foundora.model.Category;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;

import java.time.LocalDate;

/** Filters on the admin's All Reports page. Null means "any". */
public record AdminReportFilter(String text, ReportStatus status, ReportType type, Category category,
                                LocalDate from, LocalDate to) {
}
