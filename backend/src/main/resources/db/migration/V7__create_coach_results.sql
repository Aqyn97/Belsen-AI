-- Only validated, server-owned evidence and selected action IDs; never raw provider output.
CREATE TABLE coach_results (
 owner_id UUID NOT NULL, submission_id UUID NOT NULL,
 result_json TEXT NOT NULL CHECK(octet_length(result_json)<=32768 AND jsonb_typeof(result_json::jsonb)='object'),
 created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>created_at),
 PRIMARY KEY(owner_id,submission_id),
 FOREIGN KEY(owner_id,submission_id) REFERENCES coach_requests(owner_id,submission_id) ON DELETE CASCADE
);
CREATE INDEX coach_results_expiry_idx ON coach_results(expires_at);
CREATE FUNCTION guard_coach_result() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' THEN RAISE EXCEPTION 'Coach results are immutable'; END IF;
 IF NOT EXISTS (SELECT 1 FROM coach_requests r WHERE r.owner_id=NEW.owner_id AND r.submission_id=NEW.submission_id
  AND r.state IN ('COMPLETED','FAILED_PRE_DISPATCH') AND NEW.expires_at=r.replay_until AND NEW.created_at>=r.created_at) THEN
  RAISE EXCEPTION 'Coach result requires a terminal owned request';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER coach_result_guard BEFORE INSERT OR UPDATE ON coach_results FOR EACH ROW EXECUTE FUNCTION guard_coach_result();
