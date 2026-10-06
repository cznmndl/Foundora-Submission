package com.foundora.model;

import com.foundora.auth.UserRole;
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

/** A comment on a post (db/migration/V8__comments.sql). Text, one photo, or both. */
@Entity
@Table(name = "comments")
public class Comment {

    public static final int MAX_BODY = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID itemId;

    private String authorUid;

    @Column(nullable = false)
    private String authorEmail;

    @Column(nullable = false)
    private String authorName;

    /** The author's role when they commented, for the badge. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole authorRole;

    private String body;

    /** Object path in the private bucket, e.g. {@code comments/<uuid>.jpg}; null when there is no photo. */
    private String imagePath;

    @Column(nullable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(UUID itemId, String authorUid, String authorEmail, String authorName, UserRole authorRole,
                   String body, String imagePath, Instant createdAt) {
        this.itemId = itemId;
        this.authorUid = authorUid;
        this.authorEmail = authorEmail;
        this.authorName = authorName;
        this.authorRole = authorRole;
        this.body = body;
        this.imagePath = imagePath;
        this.createdAt = createdAt;
    }

    public boolean hasPhoto() {
        return imagePath != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getAuthorUid() {
        return authorUid;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public String getAuthorName() {
        return authorName;
    }

    public UserRole getAuthorRole() {
        return authorRole;
    }

    public String getBody() {
        return body;
    }

    public String getImagePath() {
        return imagePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
