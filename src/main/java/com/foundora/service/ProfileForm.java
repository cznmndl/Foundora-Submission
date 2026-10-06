package com.foundora.service;

import com.foundora.auth.UserRole;
import com.foundora.config.ProfileOptions;
import com.foundora.model.UserProfile;
import org.springframework.validation.Errors;

import java.util.regex.Pattern;

/**
 * What the profile form submits. Which fields are required depends on the signed-in role: students give
 * course, year level and section; staff and admins give a department or office. Name and email always come
 * from the Google account, never from this form.
 */
public class ProfileForm {

    private static final Pattern SECTION = Pattern.compile("[A-Za-z0-9 -]+");

    private String course;
    private String courseOther;
    private Integer yearLevel;
    private String section;
    private String department;

    public static ProfileForm from(UserProfile profile, ProfileOptions options) {
        ProfileForm form = new ProfileForm();
        if (profile == null) {
            return form;
        }
        if (profile.getCourse() != null) {
            if (options.isListedCourse(profile.getCourse())) {
                form.course = profile.getCourse();
            } else {
                form.course = ProfileOptions.OTHER;
                form.courseOther = profile.getCourse();
            }
        }
        form.yearLevel = profile.getYearLevel();
        form.section = profile.getSection();
        form.department = profile.getDepartment();
        return form;
    }

    public void validate(UserRole role, ProfileOptions options, Errors errors) {
        if (role == UserRole.STUDENT) {
            if (course == null || course.isBlank()) {
                errors.rejectValue("course", "required", "Choose your course.");
            } else if (!options.courses().contains(course)) {
                errors.rejectValue("course", "unknown", "Choose a course from the list, or “Other”.");
            } else if (ProfileOptions.OTHER.equals(course)) {
                if (courseOther == null || courseOther.isBlank()) {
                    errors.rejectValue("courseOther", "required", "You chose “Other”, so type your program.");
                } else if (courseOther.length() > UserProfile.MAX_COURSE) {
                    errors.rejectValue("courseOther", "length",
                            "Keep the program to " + UserProfile.MAX_COURSE + " characters or fewer.");
                }
            }
            if (yearLevel == null || yearLevel < 1 || yearLevel > ProfileOptions.MAX_YEAR) {
                errors.rejectValue("yearLevel", "required", "Choose your year level.");
            }
            if (section == null || section.isBlank()) {
                errors.rejectValue("section", "required", "Enter your section, e.g. O1A.");
            } else if (section.length() > UserProfile.MAX_SECTION || !SECTION.matcher(section).matches()) {
                errors.rejectValue("section", "format",
                        "Use letters, numbers and dashes only, up to " + UserProfile.MAX_SECTION + " characters.");
            }
        } else {
            if (department == null || department.isBlank()) {
                errors.rejectValue("department", "required", "Enter your department or office.");
            } else if (department.length() > UserProfile.MAX_DEPARTMENT) {
                errors.rejectValue("department", "length",
                        "Keep it to " + UserProfile.MAX_DEPARTMENT + " characters or fewer.");
            }
        }
    }

    /** The course to store: the listed course, or what the student typed for "Other". */
    public String resolvedCourse() {
        return ProfileOptions.OTHER.equals(course) ? courseOther : course;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = trim(course);
    }

    public String getCourseOther() {
        return courseOther;
    }

    public void setCourseOther(String courseOther) {
        this.courseOther = trim(courseOther);
    }

    public Integer getYearLevel() {
        return yearLevel;
    }

    public void setYearLevel(Integer yearLevel) {
        this.yearLevel = yearLevel;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section == null ? null : section.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = trim(department);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
