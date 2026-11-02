package com.foundora.auth;

import com.foundora.database.entity.Community;
import com.foundora.database.entity.UserProfile;
import org.springframework.validation.Errors;

import java.util.Locale;
import java.util.regex.Pattern;

public class ProfileForm {

    private static final Pattern SECTION = Pattern.compile("[A-Za-z0-9 -]+");

    private String community;
    private String course;
    private Integer yearLevel;
    private Integer gradeLevel;
    private String section;
    private String department;

    private String unlistedCourse;

    public static ProfileForm from(UserProfile profile, ProfileOptions options) {
        ProfileForm form = new ProfileForm();
        if (profile == null) {
            return form;
        }
        Community c = profile.effectiveCommunity();
        form.community = c == null ? null : c.name();
        if (profile.getCourse() != null) {
            if (options.isListedCourse(profile.getCourse())) {
                form.course = profile.getCourse();
            } else {
                form.unlistedCourse = profile.getCourse();
            }
        }
        form.yearLevel = profile.getYearLevel();
        form.gradeLevel = profile.getGradeLevel();
        form.section = profile.getSection();
        form.department = profile.getDepartment();
        return form;
    }

    public void validate(UserRole role, ProfileOptions options, Errors errors) {
        if (role != UserRole.STUDENT) {
            if (department == null || department.isBlank()) {
                errors.rejectValue("department", "required", "Enter your department or office.");
            } else if (department.length() > UserProfile.MAX_DEPARTMENT) {
                errors.rejectValue("department", "length",
                        "Keep it to " + UserProfile.MAX_DEPARTMENT + " characters or fewer.");
            }
            return;
        }
        Community c = resolvedCommunity();
        if (c == null) {
            errors.rejectValue("community", "required", "Choose your community.");
        } else if (c.isCollege()) {
            if (course == null || course.isBlank()) {
                errors.rejectValue("course", "required", "Choose your course.");
            } else if (!options.isListedCourse(course)) {
                errors.rejectValue("course", "unknown", "Choose a course from the list.");
            }
            if (yearLevel == null || yearLevel < 1 || yearLevel > ProfileOptions.MAX_YEAR) {
                errors.rejectValue("yearLevel", "required", "Choose your year level.");
            }
        } else if (!c.hasGrade(gradeLevel)) {
            errors.rejectValue("gradeLevel", "range", c.label() + " is Grades " + c.grades().get(0) + "–"
                    + c.grades().get(c.grades().size() - 1) + ". Choose your grade.");
        }
        if (section == null || section.isBlank()) {
            errors.rejectValue("section", "required", c != null && !c.isCollege()
                    ? "Enter your section, e.g. Rizal." : "Enter your section, e.g. O1A.");
        } else if (section.length() > UserProfile.MAX_SECTION || !SECTION.matcher(section).matches()) {
            errors.rejectValue("section", "format",
                    "Use letters, numbers and dashes only, up to " + UserProfile.MAX_SECTION + " characters.");
        }
    }

    public Community resolvedCommunity() {
        return Community.fromName(community);
    }

    public String resolvedSection() {
        if (section == null) {
            return null;
        }
        Community c = resolvedCommunity();
        return c != null && c.isCollege() ? section.toUpperCase(Locale.ROOT) : section;
    }

    public String getCommunity() {
        return community;
    }

    public void setCommunity(String community) {
        this.community = trim(community);
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = trim(course);
    }

    public Integer getYearLevel() {
        return yearLevel;
    }

    public void setYearLevel(Integer yearLevel) {
        this.yearLevel = yearLevel;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = trim(section);
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = trim(department);
    }

    public String getUnlistedCourse() {
        return unlistedCourse;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
