package com.foundora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class TimeConfig {

    /** The school's clock. Render runs in UTC, so "today" and displayed times use this zone explicitly. */
    @Bean
    public Clock clock(@Value("${foundora.time-zone:Asia/Manila}") ZoneId zone) {
        return Clock.system(zone);
    }
}
