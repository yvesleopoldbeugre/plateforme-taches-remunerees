package com.platform.participations.infrastructure;

import com.platform.participations.domain.ParticipationStatus;
import com.platform.participations.domain.TaskParticipation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskParticipationRepository extends JpaRepository<TaskParticipation, UUID> {

    List<TaskParticipation> findByUserId(UUID userId);

    List<TaskParticipation> findByTaskId(UUID taskId);

    List<TaskParticipation> findByStatusAndExpiresAtBefore(ParticipationStatus status, Instant instant);
}
