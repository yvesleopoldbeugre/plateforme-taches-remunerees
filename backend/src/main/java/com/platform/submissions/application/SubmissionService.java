package com.platform.submissions.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.exception.BusinessException;
import com.platform.submissions.domain.TaskSubmission;
import com.platform.submissions.infrastructure.TaskSubmissionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for submissions (UC-10, BR-051). Exposed to the {@code participations}
 * module, which orchestrates the overall submit flow (DAT §8.2 — participations depends on
 * submissions, never the other way).
 */
@Service
public class SubmissionService {

    private final TaskSubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    public SubmissionService(TaskSubmissionRepository submissionRepository, ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskSubmission recordSubmission(UUID participationId, UUID taskId, Map<String, Object> content,
                                            Object attachments) {
        String contentJson = toJson(content);
        String hash = sha256(contentJson);

        if (submissionRepository.existsByTaskIdAndContentHash(taskId, hash)) {
            // Stronger than the original design intent (which only "blocks auto-validation") —
            // there is no auto-validation engine yet (Phase 6), so an outright block is the
            // simplest correct interim behaviour. No FraudEvent is raised yet either: that table
            // has no owning module until Phase 10 (anti-fraude).
            throw new BusinessException("DUPLICATE_SUBMISSION_HASH", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Une soumission identique existe déjà pour cette tâche (BR-051).");
        }

        String attachmentsJson = attachments == null ? null : toJson(attachments);
        return submissionRepository.save(new TaskSubmission(participationId, taskId, contentJson, hash, attachmentsJson));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("INVALID_SUBMISSION_CONTENT", HttpStatus.BAD_REQUEST, "Contenu de soumission invalide.");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 should always be available on the JVM", e);
        }
    }
}
