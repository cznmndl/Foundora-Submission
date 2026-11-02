package com.foundora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("campusLocations")
public class CampusLocations {

    public static final String OTHER = "Other";

    private final List<String> places;

    public CampusLocations(@Value("${foundora.campus-locations:}") List<String> configured) {
        List<String> list = new ArrayList<>();
        for (String place : configured) {
            String trimmed = place.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase(OTHER) && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        list.add(OTHER);
        this.places = List.copyOf(list);
    }

    public List<String> all() {
        return places;
    }

    public boolean contains(String place) {
        return place != null && places.contains(place);
    }
}
