-- BR-051 revisited in Phase 10 (anti-fraude): the design dossier's own intent is "bloque la
-- validation automatique", not "bloque la soumission" — Phase 5 implemented the stricter form
-- because no fraud module existed yet to do anything else with the signal (see SubmissionService's
-- own comment at the time). This column lets a duplicate be recorded and flagged instead of
-- rejected outright, so validation can still fall back to manual review.
ALTER TABLE task_submissions
    ADD COLUMN duplicate_of_submission_id UUID REFERENCES task_submissions(id);
