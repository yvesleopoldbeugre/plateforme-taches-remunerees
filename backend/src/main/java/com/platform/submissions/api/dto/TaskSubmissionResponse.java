package com.platform.submissions.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.submissions.domain.TaskSubmission;
import java.time.Instant;
import java.util.UUID;

public record TaskSubmissionResponse(
        UUID id, UUID participationId, UUID taskId, Object content, Object attachments,
        UUID duplicateOfSubmissionId, Instant submittedAt
) {
    public static TaskSubmissionResponse of(TaskSubmission s, ObjectMapper objectMapper) {
        return new TaskSubmissionResponse(s.getId(), s.getParticipationId(), s.getTaskId(),
                parse(s.getContent(), objectMapper), parse(s.getAttachments(), objectMapper),
                s.getDuplicateOfSubmissionId(), s.getSubmittedAt());
    }

    private static Object parse(String json, ObjectMapper objectMapper) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
