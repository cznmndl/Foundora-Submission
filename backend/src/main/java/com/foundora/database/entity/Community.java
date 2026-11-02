package com.foundora.database.entity;

import java.util.List;
import java.util.stream.IntStream;

public enum Community {
    ELEMENTARY("Elementary", "Elementary", 1, 6),
    JUNIOR_HIGH("Junior High School", "JHS", 7, 10),
    SENIOR_HIGH("Senior High School", "SHS", 11, 12),
    COLLEGE("College", "College", 0, 0);

    private final String label;
    private final String shortLabel;
    private final int firstGrade;
    private final int lastGrade;

    Community(String label, String shortLabel, int firstGrade, int lastGrade) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.firstGrade = firstGrade;
        this.lastGrade = lastGrade;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public boolean isCollege() {
        return this == COLLEGE;
    }

    public List<Integer> grades() {
        return isCollege() ? List.of() : IntStream.rangeClosed(firstGrade, lastGrade).boxed().toList();
    }

    public boolean hasGrade(Integer grade) {
        return grade != null && !isCollege() && grade >= firstGrade && grade <= lastGrade;
    }

    public static Community fromName(String name) {
        if (name == null) {
            return null;
        }
        for (Community community : values()) {
            if (community.name().equalsIgnoreCase(name.trim())) {
                return community;
            }
        }
        return null;
    }
}
