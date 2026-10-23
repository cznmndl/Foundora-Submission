package com.foundora.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_records")
public class ClaimRecord {

    public static final int MAX_NAME = 200;
    public static final int MAX_AFFILIATION = 200;
    public static final int MAX_NOTES = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID itemId;

    private String claimantUid;
    private String claimantEmail;

    @Column(nullable = false)
    private String claimantName;

    @Column(nullable = false)
    private String claimantAffiliation;

    @Column(nullable = false)
    private Instant releasedAt;

    @Column(nullable = false)
    private String releasedByEmail;

    @Column(nullable = false)
    private String releasedByName;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.ACTIVE;

    private String disputeNote;
    private Instant statusChangedAt;
    private String statusChangedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected ClaimRecord() {
    }

    public ClaimRecord(UUID itemId, String claimantUid, String claimantEmail, String claimantName,
                       String claimantAffiliation, Instant releasedAt, String releasedByEmail,
                       String releasedByName, String notes, Instant createdAt) {
        this.itemId = itemId;
        this.claimantUid = claimantUid;
        this.claimantEmail = claimantEmail;
        this.claimantName = claimantName;
        this.claimantAffiliation = claimantAffiliation;
        this.releasedAt = releasedAt;
        this.releasedByEmail = releasedByEmail;
        this.releasedByName = releasedByName;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public void changeStatus(ClaimStatus next, String note, String adminName, Instant now) {
        this.status = next;
        if (next == ClaimStatus.DISPUTED) {
            this.disputeNote = note;
        }
        this.statusChangedAt = now;
        this.statusChangedBy = adminName;
    }

    public boolean isOpen() {
        return status != ClaimStatus.REOPENED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getClaimantUid() {
        return claimantUid;
    }

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public String getClaimantAffiliation() {
        return claimantAffiliation;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public String getReleasedByEmail() {
        return releasedByEmail;
    }

    public String getReleasedByName() {
        return releasedByName;
    }

    public String getNotes() {
        return notes;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getDisputeNote() {
        return disputeNote;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public String getStatusChangedBy() {
        return statusChangedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
