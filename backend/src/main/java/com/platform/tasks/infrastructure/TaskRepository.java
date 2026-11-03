package com.platform.tasks.infrastructure;

import com.platform.tasks.domain.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByCampaignId(UUID campaignId);

    long countByCampaignId(UUID campaignId);

    /**
     * BR-022, DAT §6.1 — a single conditional UPDATE, not a read-then-write: under concurrency
     * this guarantees {@code current_participants} never exceeds {@code max_participants}
     * without a pessimistic lock. Returns 1 if a slot was obtained, 0 if the task was full.
     */
    @Modifying
    @Query("UPDATE Task t SET t.currentParticipants = t.currentParticipants + 1 "
            + "WHERE t.id = :taskId AND t.currentParticipants < t.maxParticipants")
    int tryReserveSlot(@Param("taskId") UUID taskId);

    /** Releases a previously reserved slot (expiry, cancellation) — BR-021. */
    @Modifying
    @Query("UPDATE Task t SET t.currentParticipants = t.currentParticipants - 1 "
            + "WHERE t.id = :taskId AND t.currentParticipants > 0")
    int releaseSlot(@Param("taskId") UUID taskId);
}
