package com.platform.submissions.infrastructure;

import com.platform.submissions.domain.TaskSubmission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, UUID> {

    boolean existsByTaskIdAndContentHash(UUID taskId, String contentHash);

    List<TaskSubmission> findByParticipationId(UUID participationId);
}
