package com.platform.validation.infrastructure;

import com.platform.validation.domain.Validation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationRepository extends JpaRepository<Validation, UUID> {

    List<Validation> findBySubmissionIdOrderByReviewedAtDesc(UUID submissionId);
}
