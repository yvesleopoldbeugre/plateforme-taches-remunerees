package com.platform.submissions.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.exception.BusinessException;
import com.platform.common.exception.NotFoundException;
import com.platform.submissions.domain.TaskSubmission;
import com.platform.submissions.infrastructure.TaskSubmissionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Phase 10 revisits BR-051: the design dossier's own wording is "bloque la validation
     * automatique", not "bloque la soumission" — Phase 5 rejected duplicates outright because no
     * fraud module existed yet to raise a signal instead. Now a duplicate is recorded (not
     * rejected) and flagged via {@code duplicateOfSubmissionId}; the caller
     * ({@code ParticipationService#submit}) is responsible for publishing the fraud event and
     * {@code ValidationService#attemptAutoValidation} skips auto-approval on a flagged submission.
     */
    @Transactional
    public TaskSubmission recordSubmission(UUID participationId, UUID taskId, Map<String, Object> content,
                                            Object attachments) {
        String contentJson = toJson(content);
        String hash = sha256(contentJson);

        Optional<TaskSubmission> existing = submissionRepository.findFirstByTaskIdAndContentHashOrderBySubmittedAtAsc(taskId, hash);
        String attachmentsJson = attachments == null ? null : toJson(attachments);
        return submissionRepository.save(new TaskSubmission(participationId, taskId, contentJson, hash, attachmentsJson,
                existing.map(TaskSubmission::getId).orElse(null)));
    }

    public List<TaskSubmission> listByParticipation(UUID participationId) {
        return submissionRepository.findByParticipationId(participationId);
    }

    public TaskSubmission getSubmission(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND", "Soumission introuvable."));
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
