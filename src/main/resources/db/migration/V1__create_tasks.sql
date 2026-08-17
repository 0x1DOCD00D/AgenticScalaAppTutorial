-- V1: initial schema.
-- Flyway applies files in version order and records them in flyway_schema_history.
-- NEVER edit an applied migration; add V2__*.sql instead (enforced by the db-migrator agent).

CREATE TABLE tasks (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL DEFAULT '',
    status      VARCHAR(20)  NOT NULL DEFAULT 'Todo'
                CHECK (status IN ('Todo', 'InProgress', 'Done')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The list endpoint filters by status; index it from day one.
CREATE INDEX idx_tasks_status ON tasks (status);
