package com.platform.tasks.api;

import com.platform.tasks.api.dto.CreateTaskRequest;
import com.platform.tasks.api.dto.TaskResponse;
import com.platform.tasks.application.TaskService;
import com.platform.tasks.domain.Task;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> addTask(Authentication authentication, @PathVariable UUID campaignId,
                                                 @Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.addTask(campaignId, currentUserId(authentication), request.type(), request.title(),
                request.instructions(), request.rewardAmount(), request.maxParticipants(), request.expectedProofType(),
                request.autoValidationRules(), request.reservationTtlMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.of(task));
    }

    @GetMapping
    public List<TaskResponse> list(@PathVariable UUID campaignId) {
        return taskService.listByCampaign(campaignId).stream().map(TaskResponse::of).toList();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
