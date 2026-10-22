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

/**
 * A lost or found report. The table is created by V1__create_items.sql and reshaped by V3__report_model.sql.
 * The reporter fields always come from the signed-in account, never from the form.
 */
@Entity
@Table(name = "items")
public class Item {

    public static final int MAX_NAME = 80;
    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_LOCATION = 120;
    public static final int MAX_SPOT = 200;
    public static final int MAX_REASON = 500;
    public static final int MAX_PRIVATE_DETAILS = 500;
    public static final int MAX_DENOMINATIONS = 200;
    /** The item name and (empty) description every money report gets: the form doesn't ask for either. */
    public static final String MONEY_NAME = "Money";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType reportType;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    /** A campus place from foundora.campus-locations; older reports have free text here. Admins only. */
    @Column(nullable = false)
    private String location;

    /** Exactly where on that place, e.g. "3rd floor hallway near room 305". Null on older reports. Admins only. */
    private String locationSpot;

    /** Identifying marks, names or contents, kept out of the public description so claimants can be asked. Admins only. */
    private String privateDetails;

    @Column(nullable = false)
    private LocalDate dateLostFound;

    /** Approximate time of day it was lost or found; money reports only, optional. Admins and the reporter only. */
    private LocalTime timeLostFound;

    // Money reports only (V14). Admins and the reporter only; never on the feed, in search or on public pages.
    @Column(precision = 12, scale = 2)
    private BigDecimal moneyAmount;
    private String moneyDenominations;
    @Enumerated(EnumType.STRING)
    private MoneyContainer moneyContainer;

    /** Why SDFO handled unclaimed money under its policy (status HANDLED), and who did it, when. */
    private String handledNote;
    private String handledByName;
    private Instant handledAt;

    /** Object path inside the private item-images bucket, e.g. {@code items/<uuid>.jpg}; null when there is no photo. */
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    private String reporterUid;

    @Column(nullable = false)
    private String reporterName;

    @Column(nullable = false)
    private String reporterEmail;

    private String reviewedByEmail;
    private String reviewedByName;
    private Instant reviewedAt;
    private String reviewReason;
    private Instant claimedAt;

    /** Set when a claimed report is archived: off the feed for students, kept forever for disputes. */
    private Instant archivedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: two admins acting on the same report at once can't silently overwrite each other. */
    @Version
    @Column(nullable = false)
    private long version;

    public boolean hasPhoto() {
        return imagePath != null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** On the feed: Approved, or Claimed and not yet archived. Lost money never is: only its reporter and admins see it. */
    public boolean isOnFeed() {
        return ReportStatus.ON_FEED.contains(status) && !isArchived() && !isLostMoney();
    }

    public boolean isMoney() {
        return category == Category.MONEY;
    }

    public boolean isLostMoney() {
        return isMoney() && reportType == ReportType.LOST;
    }

    public boolean isFoundMoney() {
        return isMoney() && reportType == ReportType.FOUND;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocationSpot() {
        return locationSpot;
    }

    public void setLocationSpot(String locationSpot) {
        this.locationSpot = locationSpot;
    }

    public String getPrivateDetails() {
        return privateDetails;
    }

    public void setPrivateDetails(String privateDetails) {
        this.privateDetails = privateDetails;
    }

    public LocalDate getDateLostFound() {
        return dateLostFound;
    }

    public void setDateLostFound(LocalDate dateLostFound) {
        this.dateLostFound = dateLostFound;
    }

    public LocalTime getTimeLostFound() {
        return timeLostFound;
    }

    public void setTimeLostFound(LocalTime timeLostFound) {
        this.timeLostFound = timeLostFound;
    }

    public BigDecimal getMoneyAmount() {
        return moneyAmount;
    }

    public void setMoneyAmount(BigDecimal moneyAmount) {
        this.moneyAmount = moneyAmount;
    }

    public String getMoneyDenominations() {
        return moneyDenominations;
    }

    public void setMoneyDenominations(String moneyDenominations) {
        this.moneyDenominations = moneyDenominations;
    }

    public MoneyContainer getMoneyContainer() {
        return moneyContainer;
    }

    public void setMoneyContainer(MoneyContainer moneyContainer) {
        this.moneyContainer = moneyContainer;
    }

    public String getHandledNote() {
        return handledNote;
    }

    public String getHandledByName() {
        return handledByName;
    }

    public Instant getHandledAt() {
        return handledAt;
    }

    /** SDFO dealt with unclaimed money under its policy: the note is required and kept for the record. */
    public void markHandled(String note, String adminName, Instant now) {
        this.handledNote = note;
        this.handledByName = adminName;
        this.handledAt = now;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public String getReporterUid() {
        return reporterUid;
    }

    public void setReporterUid(String reporterUid) {
        this.reporterUid = reporterUid;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getReporterEmail() {
        return reporterEmail;
    }

    public void setReporterEmail(String reporterEmail) {
        this.reporterEmail = reporterEmail;
    }

    public String getReviewedByEmail() {
        return reviewedByEmail;
    }

    public void setReviewedByEmail(String reviewedByEmail) {
        this.reviewedByEmail = reviewedByEmail;
    }

    public String getReviewedByName() {
        return reviewedByName;
    }

    public void setReviewedByName(String reviewedByName) {
        this.reviewedByName = reviewedByName;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
