package com.foundora.config;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component("fmt")
public class Formats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm\u00a0a", Locale.ENGLISH);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final Clock clock;

    public Formats(Clock clock) {
        this.clock = clock;
    }

    public String ago(Instant time) {
        if (time == null) {
            return "";
        }
        Duration age = Duration.between(time, clock.instant());
        long minutes = age.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return plural(minutes, "minute") + " ago";
        }
        long hours = age.toHours();
        if (hours < 24) {
            return plural(hours, "hour") + " ago";
        }
        long days = age.toDays();
        if (days < 7) {
            return days == 1 ? "yesterday" : days + " days ago";
        }
        return date(time.atZone(clock.getZone()).toLocalDate());
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public String dateTime(Instant time) {
        return time == null ? "" : DATE_TIME.format(time.atZone(clock.getZone()));
    }

    public String displayDateTime(Instant time) {
        return time == null ? "" : DISPLAY_DATE_TIME.format(time.atZone(clock.getZone()));
    }

    public String dateAndTime(LocalDate date, LocalTime time) {
        return time == null ? date(date) : date(date) + ", " + time(time);
    }

    public String time(LocalTime time) {
        return time == null ? "" : TIME.format(time);
    }

    public String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return "₱" + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(amount);
    }

    public String iso(Instant time) {
        return time == null ? "" : time.toString();
    }

    public String iso(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }
}
