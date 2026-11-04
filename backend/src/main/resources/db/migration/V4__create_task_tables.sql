CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    goal_id UUID,
    title VARCHAR(200) NOT NULL CHECK (btrim(title) <> ''),
    description VARCHAR(2000),
    original_due_date DATE,
    current_due_date DATE,
    original_reporting_week_id UUID,
    estimated_duration_minutes INTEGER CHECK (estimated_duration_minutes BETWEEN 1 AND 1440),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN','COMPLETED')),
    completed_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    event_sequence BIGINT NOT NULL CHECK (event_sequence > 0),
    version BIGINT NOT NULL,
    UNIQUE(id, owner_id),
    FOREIGN KEY(goal_id, owner_id) REFERENCES goals(id, owner_id),
    FOREIGN KEY(original_reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
    CHECK ((status='COMPLETED') = (completed_at IS NOT NULL)),
    CHECK ((original_due_date IS NULL) = (original_reporting_week_id IS NULL)),
    CHECK (current_due_date IS NULL OR original_due_date IS NOT NULL)
);
CREATE INDEX tasks_owner_created_idx ON tasks(owner_id, created_at, id);
CREATE INDEX tasks_owner_current_due_idx ON tasks(owner_id, current_due_date);
CREATE INDEX tasks_owner_original_due_idx ON tasks(owner_id, original_due_date);
CREATE INDEX tasks_owner_goal_idx ON tasks(owner_id, goal_id);
CREATE TABLE task_events (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    type VARCHAR(30) NOT NULL CHECK(type IN ('CREATED','DUE_ASSIGNED','RESCHEDULED','COMPLETED','REOPENED','ARCHIVED','UPDATED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    reporting_week_id UUID NOT NULL,
    original_reporting_week_id UUID,
    original_due_date DATE,
    current_due_date DATE,
    status VARCHAR(20) NOT NULL CHECK(status IN ('OPEN','COMPLETED')),
    completed_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    FOREIGN KEY(task_id, owner_id) REFERENCES tasks(id, owner_id),
    FOREIGN KEY(reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
    FOREIGN KEY(original_reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
    UNIQUE(task_id, sequence),
    CHECK ((status='COMPLETED') = (completed_at IS NOT NULL)),
    CHECK ((original_due_date IS NULL) = (original_reporting_week_id IS NULL)),
    CHECK (current_due_date IS NULL OR original_due_date IS NOT NULL)
);
CREATE INDEX task_events_cutoff_idx ON task_events(task_id, occurred_at, sequence);
-- History is append-only, including through accidental direct SQL writes.
CREATE FUNCTION reject_task_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Task events are append-only';
END;
$$;
CREATE TRIGGER task_events_append_only BEFORE UPDATE OR DELETE ON task_events
    FOR EACH ROW EXECUTE FUNCTION reject_task_event_mutation();
