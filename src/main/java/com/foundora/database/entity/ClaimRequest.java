package com.foundora.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "claim_requests")
public class ClaimRequest {

    public static final int MIN_PROOF = 15;
    public static final int MAX_PROOF = 1000;
    public static final int MAX_NOTE = 500;
    public static final int MAX_REPLY = 1000;
    public static final int MAX_MONEY_WHERE = 200;

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

    private String requesterAffiliation;

    @Column(nullable = false)
    private String proof;

    private String imagePath;

    @Column(precision = 12, scale = 2)
    private BigDecimal moneyAmount;
    private String moneyDenominations;
    private String moneyWhere;
    private LocalDate moneyLostOn;
    private LocalTime moneyLostTime;
    @Enumerated(EnumType.STRING)
    private MoneyContainer moneyContainer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimRequestStatus status = ClaimRequestStatus.PENDING;

    private String adminNote;

    private String infoReply;

    private String reviewedByEmail;
    private String reviewedByName;
    private Instant reviewedAt;
    private Instant verifiedAt;

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

    public void describeMoney(BigDecimal amount, String denominations, String where, LocalDate lostOn,
                              LocalTime lostTime, MoneyContainer container) {
        this.moneyAmount = amount;
        this.moneyDenominations = denominations;
        this.moneyWhere = where;
        this.moneyLostOn = lostOn;
        this.moneyLostTime = lostTime;
        this.moneyContainer = container;
    }

    public boolean isMoney() {
        return moneyAmount != null;
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

    public BigDecimal getMoneyAmount() {
        return moneyAmount;
    }

    public String getMoneyDenominations() {
        return moneyDenominations;
    }

    public String getMoneyWhere() {
        return moneyWhere;
    }

    public LocalDate getMoneyLostOn() {
        return moneyLostOn;
    }

    public LocalTime getMoneyLostTime() {
        return moneyLostTime;
    }

    public MoneyContainer getMoneyContainer() {
        return moneyContainer;
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
