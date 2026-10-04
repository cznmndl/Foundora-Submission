package com.foundora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SDFO office details shown beside the feed. Set foundora.sdfo.room and foundora.sdfo.hours;
 * while they're blank, pages show a clearly marked placeholder.
 */
@Component("sdfo")
public class SdfoOffice {

    private final String room;
    private final String hours;

    public SdfoOffice(@Value("${foundora.sdfo.room:}") String room, @Value("${foundora.sdfo.hours:}") String hours) {
        this.room = room.isBlank() ? null : room.trim();
        this.hours = hours.isBlank() ? null : hours.trim();
    }

    public String room() {
        return room;
    }

    public String hours() {
        return hours;
    }
}
