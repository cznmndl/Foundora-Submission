package com.foundora.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    private static final Instant NOW = Instant.parse("2026-10-04T16:30:00Z"); // 12:30 AM Oct 5 in Manila
    private final Formats fmt = new Formats(Clock.fixed(NOW, ZoneId.of("Asia/Manila")));

    @Test
    void describesHowLongAgo() {
        assertThat(fmt.ago(NOW.minusSeconds(20))).isEqualTo("just now");
        assertThat(fmt.ago(NOW.minus(Duration.ofMinutes(1)))).isEqualTo("1 minute ago");
        assertThat(fmt.ago(NOW.minus(Duration.ofHours(2)))).isEqualTo("2 hours ago");
        assertThat(fmt.ago(NOW.minus(Duration.ofDays(1)))).isEqualTo("yesterday");
        assertThat(fmt.ago(NOW.minus(Duration.ofDays(3)))).isEqualTo("3 days ago");
        assertThat(fmt.ago(NOW.minus(Duration.ofDays(10)))).isEqualTo("Sep 25, 2026");
    }

    @Test
    void formatsInPhilippineTime() {
        assertThat(fmt.dateTime(NOW)).isEqualTo("Oct 5, 2026, 12:30 AM");
        assertThat(fmt.displayDateTime(NOW)).isEqualTo("Oct 5, 2026, 12:30\u00a0AM");
        assertThat(fmt.date(LocalDate.of(2026, 1, 9))).isEqualTo("Jan 9, 2026");
    }
}
