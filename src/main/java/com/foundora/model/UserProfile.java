package com.foundora.model;

import com.foundora.auth.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A person's profile, filled in once at first sign-in (db/migration/V7__user_profiles.sql).
 * Students give course, year level and section; staff and admins give a department or office.
 * Only admins see these details on posts and comments, so SDFO can confirm who someone is.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    public static final int MAX_COURSE = 120;
    public static final int MAX_SECTION = 20;
    public static final int MAX_DEPARTMENT = 120;

    @Id
    private String uid;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private String course;
    private Integer yearLevel;
    private String section;
    private String department;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(String uid, String email, String fullName, UserRole role, Instant now) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Whether the details this role needs are filled in. Checked against the role of the current sign-in,
     * so a student who becomes an admin is asked for a department once.
     */
    public boolean isCompleteFor(UserRole currentRole) {
        if (currentRole == UserRole.STUDENT) {
            return hasText(course) && yearLevel != null && hasText(section);
        }
        return hasText(department);
    }

    /** One line for admins, e.g. "BS Information Technology · 2nd year · O1A" or "Registrar's Office". */
    public String summary() {
        if (role == UserRole.STUDENT && hasText(course)) {
            StringBuilder s = new StringBuilder(course);
            if (yearLevel != null) {
                s.append(" · ").append(yearLabel(yearLevel));
            }
            if (hasText(section)) {
                s.append(" · ").append(section);
            }
            return s.toString();
        }
        if (hasText(department)) {
            return department;
        }
        return hasText(course) ? course : "";
    }

    public static String yearLabel(int year) {
        return switch (year) {
            case 1 -> "1st year";
            case 2 -> "2nd year";
            case 3 -> "3rd year";
            default -> year + "th year";
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
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
        this.section = section;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
