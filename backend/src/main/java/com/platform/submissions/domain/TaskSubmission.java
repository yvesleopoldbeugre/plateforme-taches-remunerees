package com.platform.submissions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps to {@code task_submissions} (DAT §4.2). {@code content}/{@code attachments} are raw JSON. */
@Entity
@Table(name = "task_submissions")
public class TaskSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "participation_id", nullable = false)
    private UUID participationId;

    /** Denormalized from the participation's task — see V5 migration comment (DAT §8.2). */
    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String attachments;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    protected TaskSubmission() {
        // JPA
    }

    public TaskSubmission(UUID participationId, UUID taskId, String content, String contentHash, String attachments) {
        this.participationId = participationId;
        this.taskId = taskId;
        this.content = content;
        this.contentHash = contentHash;
        this.attachments = attachments;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParticipationId() {
        return participationId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getContent() {
        return content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getAttachments() {
        return attachments;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
