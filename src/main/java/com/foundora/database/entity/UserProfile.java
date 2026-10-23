package com.foundora.database.entity;

import com.foundora.auth.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

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

    @Enumerated(EnumType.STRING)
    private Community community;

    private String course;
    private Integer yearLevel;
    private Integer gradeLevel;
    private String section;
    private String department;

    private String pinHash;

    @Column(nullable = false)
    private int pinFailedAttempts;

    private Instant pinLockedUntil;

    @Column(nullable = false)
    private boolean pinResetRequested;

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

    public boolean isCompleteFor(UserRole currentRole) {
        if (currentRole != UserRole.STUDENT) {
            return hasText(department);
        }
        Community c = effectiveCommunity();
        if (c == null || !hasText(section)) {
            return false;
        }
        return c.isCollege() ? hasText(course) && yearLevel != null : c.hasGrade(gradeLevel);
    }

    public Community effectiveCommunity() {
        if (community != null) {
            return community;
        }
        return hasText(course) ? Community.COLLEGE : null;
    }

    public String summary() {
        Community c = effectiveCommunity();
        if (role == UserRole.STUDENT && c != null && !c.isCollege()) {
            StringBuilder s = new StringBuilder(c.shortLabel());
            if (gradeLevel != null) {
                s.append(" · Grade ").append(gradeLevel);
            }
            if (hasText(section)) {
                s.append(" · Section ").append(section);
            }
            return s.toString();
        }
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

    public Community getCommunity() {
        return community;
    }

    public void setCommunity(Community community) {
        this.community = community;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
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

    public boolean hasPin() {
        return pinHash != null;
    }

    public void clearPin() {
        pinHash = null;
        pinFailedAttempts = 0;
        pinLockedUntil = null;
        pinResetRequested = false;
    }

    public void copyPinFrom(UserProfile other) {
        pinHash = other.pinHash;
        pinFailedAttempts = other.pinFailedAttempts;
        pinLockedUntil = other.pinLockedUntil;
        pinResetRequested = other.pinResetRequested;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public int getPinFailedAttempts() {
        return pinFailedAttempts;
    }

    public void setPinFailedAttempts(int pinFailedAttempts) {
        this.pinFailedAttempts = pinFailedAttempts;
    }

    public Instant getPinLockedUntil() {
        return pinLockedUntil;
    }

    public void setPinLockedUntil(Instant pinLockedUntil) {
        this.pinLockedUntil = pinLockedUntil;
    }

    public boolean isPinResetRequested() {
        return pinResetRequested;
    }

    public void setPinResetRequested(boolean pinResetRequested) {
        this.pinResetRequested = pinResetRequested;
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
