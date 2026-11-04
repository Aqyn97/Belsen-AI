CREATE TABLE habits (
    id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL CHECK(btrim(title) <> ''), description VARCHAR(2000),
    archived_at TIMESTAMPTZ, archive_effective_date DATE,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK(version >= 0), revision_sequence BIGINT NOT NULL CHECK(revision_sequence > 0),
    UNIQUE(id, owner_id), CHECK((archived_at IS NULL) = (archive_effective_date IS NULL)),
    CHECK(updated_at >= created_at)
);
CREATE INDEX habits_owner_created_idx ON habits(owner_id, created_at, id);
CREATE TABLE habit_schedule_versions (
    id UUID PRIMARY KEY, habit_id UUID NOT NULL, owner_id UUID NOT NULL,
    sequence BIGINT NOT NULL CHECK(sequence > 0), effective_from DATE NOT NULL,
    start_date DATE NOT NULL, end_date DATE, weekdays_mask SMALLINT NOT NULL,
    enabled BOOLEAN NOT NULL, recorded_at TIMESTAMPTZ NOT NULL, reporting_week_id UUID NOT NULL,
    FOREIGN KEY(habit_id, owner_id) REFERENCES habits(id, owner_id),
    FOREIGN KEY(reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
    UNIQUE(habit_id, sequence), UNIQUE(id, habit_id, owner_id),
    CHECK(end_date IS NULL OR end_date >= start_date),
    CHECK((enabled AND weekdays_mask BETWEEN 1 AND 127) OR (NOT enabled AND weekdays_mask = 0))
);
CREATE INDEX habit_schedules_effective_idx ON habit_schedule_versions(habit_id, effective_from, sequence);
CREATE TABLE habit_checkins (
    id UUID PRIMARY KEY, habit_id UUID NOT NULL, owner_id UUID NOT NULL, local_date DATE NOT NULL,
    reporting_week_id UUID NOT NULL, schedule_version_id UUID NOT NULL,
    completed BOOLEAN NOT NULL, rest_planned BOOLEAN NOT NULL,
    version BIGINT NOT NULL CHECK(version >= 0), event_sequence BIGINT NOT NULL CHECK(event_sequence > 0),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(habit_id, owner_id) REFERENCES habits(id, owner_id),
    FOREIGN KEY(reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
    FOREIGN KEY(schedule_version_id, habit_id, owner_id) REFERENCES habit_schedule_versions(id, habit_id, owner_id),
    UNIQUE(habit_id, local_date), UNIQUE(id, habit_id, owner_id),
    CHECK(NOT(completed AND rest_planned)), CHECK(updated_at >= created_at)
);
CREATE INDEX habit_checkins_owner_week_date_idx ON habit_checkins(owner_id, reporting_week_id, local_date);
CREATE TABLE habit_checkin_events (
    id UUID PRIMARY KEY, checkin_id UUID NOT NULL, habit_id UUID NOT NULL, owner_id UUID NOT NULL,
    sequence BIGINT NOT NULL CHECK(sequence > 0),
    action VARCHAR(30) NOT NULL CHECK(action IN ('COMPLETED','UNDONE','REST_PLANNED','REST_CANCELLED')),
    accepted_at TIMESTAMPTZ NOT NULL, completed BOOLEAN NOT NULL, rest_planned BOOLEAN NOT NULL,
    reason VARCHAR(500), reporting_week_id UUID NOT NULL,
    FOREIGN KEY(checkin_id, habit_id, owner_id) REFERENCES habit_checkins(id, habit_id, owner_id),
    FOREIGN KEY(reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
    UNIQUE(checkin_id, sequence), CHECK(NOT(completed AND rest_planned)),
    CHECK((action='COMPLETED' AND completed AND NOT rest_planned)
       OR (action='UNDONE' AND NOT completed AND NOT rest_planned)
       OR (action='REST_PLANNED' AND rest_planned AND NOT completed AND reason IS NOT NULL AND btrim(reason) <> '')
       OR (action='REST_CANCELLED' AND NOT completed AND NOT rest_planned))
);
CREATE INDEX habit_events_cutoff_idx ON habit_checkin_events(checkin_id, accepted_at, sequence);
CREATE TABLE reporting_timezone_revisions (
    id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id),
    sequence BIGINT NOT NULL CHECK(sequence > 0), effective_week_start DATE NOT NULL,
    timezone VARCHAR(100) NOT NULL CHECK(btrim(timezone) <> ''), recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE(owner_id, sequence), CHECK(extract(isodow FROM effective_week_start) = 1)
);
CREATE INDEX reporting_timezone_effective_idx ON reporting_timezone_revisions(owner_id, effective_week_start, sequence);
CREATE FUNCTION reject_habit_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Habit and calendar history are append-only';
END;
$$;
CREATE TRIGGER habit_schedules_append_only BEFORE UPDATE OR DELETE ON habit_schedule_versions
    FOR EACH ROW EXECUTE FUNCTION reject_habit_history_mutation();
CREATE TRIGGER habit_events_append_only BEFORE UPDATE OR DELETE ON habit_checkin_events
    FOR EACH ROW EXECUTE FUNCTION reject_habit_history_mutation();
CREATE TRIGGER reporting_timezone_append_only BEFORE UPDATE OR DELETE ON reporting_timezone_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_habit_history_mutation();
-- Cross-row eligibility, local-date/week membership, cutoff rules, IANA zone validity,
-- monotonic acceptance times and current-state/event agreement are enforced by services.
CREATE TRIGGER reporting_weeks_immutable BEFORE UPDATE OR DELETE ON reporting_weeks
    FOR EACH ROW EXECUTE FUNCTION reject_habit_history_mutation();
