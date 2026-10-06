package com.foundora.config;

import com.foundora.model.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Choices on the profile form: the course list from foundora.profile.courses (plus "Other") and year levels. */
@Component("profileOptions")
public class ProfileOptions {

    public static final String OTHER = "Other";
    public static final int MAX_YEAR = 5;

    private final List<String> courses;

    public ProfileOptions(@Value("${foundora.profile.courses:}") List<String> configured) {
        List<String> list = new ArrayList<>();
        for (String course : configured) {
            String trimmed = course.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase(OTHER) && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        list.add(OTHER);
        this.courses = List.copyOf(list);
    }

    public List<String> courses() {
        return courses;
    }

    public boolean isListedCourse(String course) {
        return course != null && !OTHER.equals(course) && courses.contains(course);
    }

    public List<Integer> years() {
        return List.of(1, 2, 3, 4, 5);
    }

    public String yearLabel(int year) {
        return UserProfile.yearLabel(year);
    }
}
