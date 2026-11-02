package com.foundora.moderation;

import com.foundora.database.entity.Category;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;

import java.time.LocalDate;

public record AdminReportFilter(String text, ReportStatus status, ReportType type, Category category,
                                LocalDate from, LocalDate to, String location, Boolean archived) {

    public AdminReportFilter(String text, ReportStatus status, ReportType type, Category category,
                             LocalDate from, LocalDate to) {
        this(text, status, type, category, from, to, null, null);
    }
}
