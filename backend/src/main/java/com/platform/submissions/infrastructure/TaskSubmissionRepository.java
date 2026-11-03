package com.platform.submissions.infrastructure;

import com.platform.submissions.domain.TaskSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, UUID> {

    /** The earliest submission with this hash on this task — what a duplicate points back at. */
    Optional<TaskSubmission> findFirstByTaskIdAndContentHashOrderBySubmittedAtAsc(UUID taskId, String contentHash);

    List<TaskSubmission> findByParticipationId(UUID participationId);
}
