package com.platform.validation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deliberately minimal (US-052 is MUST for the MVP, but the rule language is not): a task opts
 * into auto-validation by setting {@code auto_validation_rules} to
 * {@code {"requiredFields": ["q1", "q2"]}} — every listed key must be present and non-blank in
 * the submitted content. No task sets this by default, so auto-validation is opt-in per task;
 * anything else (missing rules, malformed JSON, unmet rules) simply falls back to manual
 * review, which is always correct. A richer rule language (expected values, numeric ranges...)
 * is a natural later refinement, not a Phase 6 requirement.
 */
@Component
public class AutoValidationRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AutoValidationRuleEvaluator.class);

    private final ObjectMapper objectMapper;

    public AutoValidationRuleEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean matches(String autoValidationRulesJson, Map<String, Object> content) {
        if (autoValidationRulesJson == null || autoValidationRulesJson.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> rules = objectMapper.readValue(autoValidationRulesJson, Map.class);
            Object requiredFields = rules.get("requiredFields");
            if (!(requiredFields instanceof List<?> fields) || fields.isEmpty()) {
                return false;
            }
            for (Object field : fields) {
                Object value = content.get(String.valueOf(field));
                if (value == null || (value instanceof String s && s.isBlank())) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Could not evaluate auto_validation_rules — falling back to manual review", e);
            return false;
        }
    }
}
