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

/** One line of the admin activity log. Created by db/migration/V4__activity_log.sql. */
@Entity
@Table(name = "activity_log")
public class ActivityLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID itemId;

    /**
     * Copy of the item name at the time (for account actions, the person's name and email), so the log
     * stays readable on its own.
     */
    @Column(nullable = false)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityAction action;

    @Column(nullable = false)
    private String actorEmail;

    @Column(nullable = false)
    private String actorName;

    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    protected ActivityLogEntry() {
    }

    public ActivityLogEntry(Item item, ActivityAction action, String actorEmail, String actorName,
                            String reason, Instant createdAt) {
        this.itemId = item.getId();
        this.itemName = item.getItemName();
        this.action = action;
        this.actorEmail = actorEmail;
        this.actorName = actorName;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public ActivityLogEntry(UserProfile account, ActivityAction action, String actorEmail, String actorName,
                            Instant createdAt) {
        this.itemName = account.getFullName() + " (" + account.getEmail() + ")";
        this.action = action;
        this.actorEmail = actorEmail;
        this.actorName = actorName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public ActivityAction getAction() {
        return action;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getActorName() {
        return actorName;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
