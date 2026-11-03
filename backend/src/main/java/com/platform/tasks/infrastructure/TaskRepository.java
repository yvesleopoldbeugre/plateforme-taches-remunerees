package com.platform.tasks.infrastructure;

import com.platform.tasks.domain.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByCampaignId(UUID campaignId);

    long countByCampaignId(UUID campaignId);
}
