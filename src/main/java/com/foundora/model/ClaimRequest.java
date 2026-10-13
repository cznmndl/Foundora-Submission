package com.foundora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone saying "this is mine" about a found item (db/migration/V11__claim_requests.sql). The proof is seen
 * only by the requester and admins. Never deleted: closed requests stay for disputes.
 */
@Entity
@Table(name = "claim_requests")
public class ClaimRequest {

    public static final int MIN_PROOF = 15;
    public static final int MAX_PROOF = 1000;
    public static final int MAX_NOTE = 500;
    public static final int MAX_REPLY = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private String requesterUid;

    @Column(nullable = false)
    private String requesterEmail;

    @Column(nullable = false)
    private String requesterName;

    /** Course, year and section, or department, copied from the profile when the request was made. */
    private String requesterAffiliation;

    @Column(nullable = false)
    private String proof;

    /** Optional photo in the private bucket, under {@code claims/}. */
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimRequestStatus status = ClaimRequestStatus.PENDING;

    /** The admin's question (NEEDS_INFO) or reason (DENIED). */
    private String adminNote;

    /** The requester's answer to the admin's question. */
    private String infoReply;

    private String reviewedByEmail;
    private String reviewedByName;
    private Instant reviewedAt;
    private Instant verifiedAt;

    /** When the pickup pass ends, while VERIFIED. */
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ClaimRequest() {
    }

    public ClaimRequest(UUID itemId, String requesterUid, String requesterEmail, String requesterName,
                        String requesterAffiliation, String proof, String imagePath, Instant now) {
        this.itemId = itemId;
        this.requesterUid = requesterUid;
        this.requesterEmail = requesterEmail;
        this.requesterName = requesterName;
        this.requesterAffiliation = requesterAffiliation;
        this.proof = proof;
        this.imagePath = imagePath;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    public boolean hasPhoto() {
        return imagePath != null;
    }

    public boolean isRequester(String email) {
        return requesterEmail.equalsIgnoreCase(email);
    }

    /** An admin's decision: verify, deny or ask for more information. */
    public void review(ClaimRequestStatus next, String note, String adminEmail, String adminName, Instant now) {
        this.status = next;
        this.adminNote = note;
        this.reviewedByEmail = adminEmail;
        this.reviewedByName = adminName;
        this.reviewedAt = now;
        this.updatedAt = now;
        if (next == ClaimRequestStatus.NEEDS_INFO) {
            this.infoReply = null;
        }
    }

    public void verify(String adminEmail, String adminName, Instant now, Instant expiresAt) {
        review(ClaimRequestStatus.VERIFIED, null, adminEmail, adminName, now);
        this.verifiedAt = now;
        this.expiresAt = expiresAt;
    }

    public void extendPass(Instant newExpiry, Instant now) {
        this.expiresAt = newExpiry;
        this.updatedAt = now;
    }

    public void reply(String text, Instant now) {
        this.infoReply = text;
        this.status = ClaimRequestStatus.PENDING;
        this.updatedAt = now;
    }

    /** Ends the request without an admin decision on it: withdrawn, expired, released or closed. */
    public void end(ClaimRequestStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getRequesterUid() {
        return requesterUid;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getRequesterAffiliation() {
        return requesterAffiliation;
    }

    public String getProof() {
        return proof;
    }

    public String getImagePath() {
        return imagePath;
    }

    public ClaimRequestStatus getStatus() {
        return status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public String getInfoReply() {
        return infoReply;
    }

    public String getReviewedByEmail() {
        return reviewedByEmail;
    }

    public String getReviewedByName() {
        return reviewedByName;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
