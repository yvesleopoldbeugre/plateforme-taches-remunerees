-- BR-051 (duplicate-submission detection) is scoped "sur la même tâche" — but task_submissions
-- only referenced the participation, and participations/submissions are separate modules that
-- must not reach into each other's entities (DAT §8.2). Denormalizing task_id here lets the
-- submissions module answer "same task?" with its own query, no cross-module join.
ALTER TABLE task_submissions
    ADD COLUMN task_id UUID NOT NULL REFERENCES tasks(id);

CREATE INDEX idx_submissions_task_hash ON task_submissions(task_id, content_hash);
