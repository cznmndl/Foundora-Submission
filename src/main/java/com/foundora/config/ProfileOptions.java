package com.foundora.config;

import com.foundora.model.Community;
import com.foundora.model.UserProfile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Choices on the profile form: communities, grade levels, college year levels, and DLSL's college programs
 * grouped by college (shown with optgroup).
 */
@Component("profileOptions")
public class ProfileOptions {

    public static final int MAX_YEAR = 5;

    /** One college and its programs. */
    public record CourseGroup(String college, List<String> courses) {
    }

    private static final List<CourseGroup> COURSES = List.of(
            new CourseGroup("College of Business, Economics, Accountancy and Management", List.of(
                    "BS Accountancy", "BS Accounting Information System", "BS Legal Management",
                    "BS Entrepreneurship", "BS Management Technology", "BSBA Financial Management",
                    "BSBA Marketing Management", "Certificate in Entrepreneurship")),
            new CourseGroup("College of Education, Arts and Sciences", List.of(
                    "Bachelor of Elementary Education", "Bachelor of Secondary Education", "AB Communication",
                    "Bachelor of Multimedia Arts", "BS Biology", "BS Psychology")),
            new CourseGroup("College of International Hospitality and Tourism Management", List.of(
                    "BS Hospitality Management", "BS Tourism Management")),
            new CourseGroup("College of Information Technology and Engineering", List.of(
                    "BS Architecture", "BS Computer Engineering", "BS Computer Science",
                    "BS Electrical Engineering", "BS Electronics Engineering",
                    "BS Entertainment and Multimedia Computing", "BS Industrial Engineering",
                    "BS Information Technology", "Associate in Computer Technology")),
            new CourseGroup("College of Criminal Justice Education", List.of("Bachelor of Forensic Science")),
            // Placeholders until the programs are listed: each college is one choice for now.
            new CourseGroup("College of Law", List.of("College of Law")),
            new CourseGroup("College of Nursing", List.of("College of Nursing")));

    private static final List<String> ALL_COURSES =
            COURSES.stream().flatMap(g -> g.courses().stream()).toList();

    public List<CourseGroup> courseGroups() {
        return COURSES;
    }

    public List<String> courses() {
        return ALL_COURSES;
    }

    public boolean isListedCourse(String course) {
        return course != null && ALL_COURSES.contains(course);
    }

    public List<Community> communities() {
        return List.of(Community.values());
    }

    /** Basic education communities, for the grade dropdown's optgroups. */
    public List<Community> basicEducation() {
        return List.of(Community.ELEMENTARY, Community.JUNIOR_HIGH, Community.SENIOR_HIGH);
    }

    public List<Integer> years() {
        return List.of(1, 2, 3, 4, 5);
    }

    public String yearLabel(int year) {
        return UserProfile.yearLabel(year);
    }
}
