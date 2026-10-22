package com.foundora.moderation;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * The admin's claim record form. The claimant is either a registered user, picked by email (their name and
 * course/department then come from their profile), or typed in by hand.
 */
public class ClaimForm {

    private String claimantEmail;
    private String claimantName;
    private String claimantAffiliation;

    /** When the item was handed over, in the school's time zone (from a datetime-local input). */
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime releasedAt;

    private String notes;

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public void setClaimantEmail(String claimantEmail) {
        this.claimantEmail = blankToNull(claimantEmail);
    }

    public String getClaimantName() {
        return claimantName;
    }

    public void setClaimantName(String claimantName) {
        this.claimantName = blankToNull(claimantName);
    }

    public String getClaimantAffiliation() {
        return claimantAffiliation;
    }

    public void setClaimantAffiliation(String claimantAffiliation) {
        this.claimantAffiliation = blankToNull(claimantAffiliation);
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = blankToNull(notes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
