package com.foundora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component("sdfo")
public class SdfoOffice {

    private final String room;
    private final String hours;
    private final Set<String> emails;

    public SdfoOffice(@Value("${foundora.sdfo.room:}") String room, @Value("${foundora.sdfo.hours:}") String hours,
                      @Value("${foundora.sdfo.emails:}") List<String> emails) {
        this.room = room.isBlank() ? null : room.trim();
        this.hours = hours.isBlank() ? null : hours.trim();
        this.emails = emails.stream().map(e -> e.trim().toLowerCase(Locale.ROOT)).filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String room() {
        return room;
    }

    public String hours() {
        return hours;
    }

    public boolean isSdfoAccount(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
