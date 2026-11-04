CREATE TABLE reporting_weeks (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), week_start DATE NOT NULL,
 timezone VARCHAR(100) NOT NULL, starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 UNIQUE(owner_id, week_start), CHECK (extract(isodow from week_start) = 1), CHECK (ends_at > starts_at),
 UNIQUE(id, owner_id)
);
CREATE INDEX reporting_weeks_owner_window_idx ON reporting_weeks(owner_id, starts_at, ends_at);
CREATE TABLE goals (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), title VARCHAR(200) NOT NULL,
 description VARCHAR(2000), target NUMERIC(19,4) NOT NULL CHECK(target > 0), unit VARCHAR(50) NOT NULL,
 start_date DATE, deadline DATE, archived_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL,
 CHECK(btrim(title) <> ''), CHECK(btrim(unit) <> ''), CHECK(deadline IS NULL OR start_date IS NULL OR deadline >= start_date),
 UNIQUE(id, owner_id)
);
CREATE INDEX goals_owner_created_idx ON goals(owner_id, created_at, id);
CREATE TABLE goal_weekly_plans (
 id UUID PRIMARY KEY, goal_id UUID NOT NULL, owner_id UUID NOT NULL, reporting_week_id UUID NOT NULL,
 planned_units NUMERIC(19,4) NOT NULL CHECK(planned_units > 0), version BIGINT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(goal_id, owner_id) REFERENCES goals(id, owner_id),
 FOREIGN KEY(reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
 UNIQUE(goal_id, reporting_week_id)
);
CREATE TABLE goal_progress_entries (
 id UUID PRIMARY KEY, goal_id UUID NOT NULL, owner_id UUID NOT NULL, reporting_week_id UUID NOT NULL,
 submission_id UUID NOT NULL, quantity NUMERIC(19,4) NOT NULL CHECK(quantity > 0), effective_date DATE NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, voided_at TIMESTAMPTZ,
 FOREIGN KEY(goal_id, owner_id) REFERENCES goals(id, owner_id),
 FOREIGN KEY(reporting_week_id, owner_id) REFERENCES reporting_weeks(id, owner_id),
 UNIQUE(goal_id, submission_id), CHECK(voided_at IS NULL OR voided_at >= created_at)
);
CREATE INDEX goal_progress_goal_date_idx ON goal_progress_entries(goal_id, effective_date, id);
