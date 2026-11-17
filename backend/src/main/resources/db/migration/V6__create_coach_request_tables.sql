-- Operational request metadata only; never prompts, provider responses or recommendation text.
CREATE TABLE coach_requests (
 owner_id UUID NOT NULL REFERENCES users(id), submission_id UUID NOT NULL,
 fingerprint CHAR(64) NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 state VARCHAR(24) NOT NULL CHECK(state IN ('ACCEPTED','RESERVED','PROCESSING','COMPLETED','FAILED_PRE_DISPATCH','OUTCOME_UNKNOWN')),
 created_at TIMESTAMPTZ NOT NULL, deadline TIMESTAMPTZ NOT NULL,
 dispatched_at TIMESTAMPTZ, finished_at TIMESTAMPTZ,
 replay_until TIMESTAMPTZ NOT NULL, retain_until TIMESTAMPTZ NOT NULL,
 reserved_usd NUMERIC(14,8) NOT NULL DEFAULT 0 CHECK(reserved_usd>=0 AND reserved_usd<=1),
 PRIMARY KEY(owner_id,submission_id),
 CHECK(deadline>created_at AND replay_until=created_at+INTERVAL '24 hours' AND retain_until=created_at+INTERVAL '768 hours'),
 CHECK(dispatched_at IS NULL OR dispatched_at>=created_at),
 CHECK(finished_at IS NULL OR finished_at>=created_at),
 CHECK((state IN ('PROCESSING','COMPLETED','OUTCOME_UNKNOWN'))=(dispatched_at IS NOT NULL)),
 CHECK((state IN ('COMPLETED','FAILED_PRE_DISPATCH','OUTCOME_UNKNOWN'))=(finished_at IS NOT NULL))
);
CREATE INDEX coach_requests_expiry_idx ON coach_requests(retain_until);
CREATE INDEX coach_requests_active_idx ON coach_requests(state,deadline);
CREATE INDEX coach_requests_admission_idx ON coach_requests(created_at);
CREATE INDEX coach_requests_owner_admission_idx ON coach_requests(owner_id,created_at);
CREATE TABLE coach_daily_usage (
 quota_day DATE NOT NULL, scope VARCHAR(10) NOT NULL CHECK(scope IN ('GLOBAL','ACCOUNT')),
 owner_id UUID REFERENCES users(id), attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),
 reserved_usd NUMERIC(14,8) NOT NULL DEFAULT 0 CHECK(reserved_usd>=0 AND reserved_usd<=1),
 CHECK((scope='ACCOUNT')=(owner_id IS NOT NULL)),
 UNIQUE NULLS NOT DISTINCT(quota_day,scope,owner_id)
);
CREATE FUNCTION guard_coach_request_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.owner_id<>OLD.owner_id OR NEW.submission_id<>OLD.submission_id OR NEW.fingerprint<>OLD.fingerprint
    OR NEW.created_at<>OLD.created_at OR NEW.deadline<>OLD.deadline
    OR NEW.replay_until<>OLD.replay_until OR NEW.retain_until<>OLD.retain_until THEN
  RAISE EXCEPTION 'Coach request identity is immutable';
 END IF;
 IF NOT ((OLD.state='ACCEPTED' AND NEW.state IN ('RESERVED','FAILED_PRE_DISPATCH'))
    OR (OLD.state='RESERVED' AND NEW.state IN ('PROCESSING','FAILED_PRE_DISPATCH'))
    OR (OLD.state='PROCESSING' AND NEW.state IN ('COMPLETED','OUTCOME_UNKNOWN'))) THEN
  RAISE EXCEPTION 'Invalid coach request transition';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER coach_request_transitions BEFORE UPDATE ON coach_requests
 FOR EACH ROW EXECUTE FUNCTION guard_coach_request_transition();
