package com.belsenai.coach;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import com.belsenai.common.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short serialized ledger transactions. Never wrap analytics or a future network call. */
@Service
@Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
public class CoachRequests {
    public enum State { ACCEPTED, RESERVED, PROCESSING, COMPLETED, FAILED_PRE_DISPATCH, OUTCOME_UNKNOWN }
    public record Request(State state,String fingerprint,Instant replayUntil) { }
    public record Claim(Request request,boolean created) { }
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public CoachRequests(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc;this.clock=clock; }
    // Fixed application-owned lock serializes claims, reservations, transitions and rollover checks.
    Instant lock() {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        jdbc.execute("SELECT pg_advisory_xact_lock(72610401)");
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("UPDATE coach_requests SET state=CASE WHEN dispatched_at IS NULL THEN 'FAILED_PRE_DISPATCH' ELSE 'OUTCOME_UNKNOWN' END,finished_at=? WHERE state IN ('ACCEPTED','RESERVED','PROCESSING') AND deadline<=?",ts(now),ts(now));
        return now;
    }
    public Request claim(UUID owner,UUID key,String fingerprint) { return admit(owner,key,fingerprint).request(); }
    public Claim admit(UUID owner,UUID key,String fingerprint) {
        if(fingerprint==null || !fingerprint.matches("[0-9a-f]{64}")) throw new ApiException(400,"Invalid request fingerprint.");
        Instant now=lock();
        var old=find(owner,key);
        if(old!=null) {
            if(!old.fingerprint().equals(fingerprint)) throw new ApiException(409,"Submission identifier conflicts with an existing request.");
            if(!now.isBefore(old.replayUntil())) throw new ApiException(410,"Request replay has expired. Use a new submission identifier.");
            return new Claim(old,false);
        }
        cleanupAt(now);
        if(jdbc.queryForObject("SELECT count(*) FROM coach_requests WHERE created_at>?",Long.class,ts(now.minusSeconds(60)))>=100
                || jdbc.queryForObject("SELECT count(*) FROM coach_requests WHERE owner_id=? AND created_at>?",Long.class,owner,ts(now.minusSeconds(60)))>=10) throw new CoachLimitException("Coach admission limit reached.",60);
        if(jdbc.queryForObject("SELECT count(*) FROM coach_requests",Long.class)>=100000) throw new ApiException(503,"Coach request storage is at capacity.");
        jdbc.update("INSERT INTO coach_requests(owner_id,submission_id,fingerprint,state,created_at,deadline,replay_until,retain_until) VALUES(?,?,?,'ACCEPTED',?,?,?,?)",owner,key,fingerprint,ts(now),ts(now.plusSeconds(20)),ts(now.plus(Duration.ofHours(24))),ts(now.plus(Duration.ofDays(32))));
        return new Claim(find(owner,key),true);
    }
    /** Bounded maintenance, also invoked by admission. Uncertain dispatch is never erased automatically. */
    public int cleanup() { return cleanupAt(lock()); }
    private int cleanupAt(Instant now) {
        jdbc.update("DELETE FROM coach_results WHERE (owner_id,submission_id) IN (SELECT owner_id,submission_id FROM coach_results WHERE expires_at<=? ORDER BY expires_at LIMIT 100)",ts(now));
        int deleted=jdbc.update("DELETE FROM coach_requests WHERE (owner_id,submission_id) IN (SELECT owner_id,submission_id FROM coach_requests WHERE retain_until<=? AND state IN ('COMPLETED','FAILED_PRE_DISPATCH') ORDER BY retain_until LIMIT 100)",ts(now));
        jdbc.update("DELETE FROM coach_daily_usage WHERE ctid IN (SELECT ctid FROM coach_daily_usage WHERE quota_day<? ORDER BY quota_day LIMIT 100)",now.atZone(ZoneOffset.UTC).toLocalDate().minusDays(32));
        return deleted;
    }
    /** Foundation only: the future adapter must supply a validated worst-case price. */
    public void reserve(UUID owner,UUID key,BigDecimal maximumCost) { reserveInternal(owner,key,maximumCost,false); }
    public void reserveFree(UUID owner,UUID key) { reserveInternal(owner,key,BigDecimal.ZERO,true); }
    private void reserveInternal(UUID owner,UUID key,BigDecimal maximumCost,boolean free) {
        if(maximumCost==null || (free ? maximumCost.signum()<0 : maximumCost.signum()<=0) || maximumCost.scale()>8 || maximumCost.compareTo(BigDecimal.ONE)>0) throw new ApiException(400,"Invalid cost reservation.");
        Instant now=lock();require(owner,key,State.ACCEPTED);
        if(jdbc.queryForObject("SELECT count(*) FROM coach_requests WHERE state IN ('RESERVED','PROCESSING','OUTCOME_UNKNOWN')",Long.class)>=2
                || jdbc.queryForObject("SELECT count(*) FROM coach_requests WHERE owner_id=? AND state IN ('RESERVED','PROCESSING','OUTCOME_UNKNOWN')",Long.class,owner)>0) throw new CoachLimitException("Coach analysis capacity is unavailable.",20);
        LocalDate day=now.atZone(ZoneOffset.UTC).toLocalDate();
        jdbc.update("INSERT INTO coach_daily_usage(quota_day,scope) VALUES(?,'GLOBAL') ON CONFLICT DO NOTHING",day);
        jdbc.update("INSERT INTO coach_daily_usage(quota_day,scope,owner_id) VALUES(?,'ACCOUNT',?) ON CONFLICT DO NOTHING",day,owner);
        var daily=jdbc.queryForObject("SELECT reserved_usd FROM coach_daily_usage WHERE quota_day=? AND scope='GLOBAL'",BigDecimal.class,day);
        var monthly=jdbc.queryForObject("SELECT coalesce(sum(reserved_usd),0) FROM coach_daily_usage WHERE scope='GLOBAL' AND quota_day>=? AND quota_day<?",BigDecimal.class,day.withDayOfMonth(1),day.withDayOfMonth(1).plusMonths(1));
        int count=jdbc.queryForObject("SELECT attempts FROM coach_daily_usage WHERE quota_day=? AND owner_id=?",Integer.class,day,owner);
        if(monthly.add(maximumCost).compareTo(BigDecimal.TEN)>0) throw new CoachLimitException("Coach monthly reservation limit reached.",Math.max(1,Duration.between(now,day.withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant()).toSeconds()));
        if(count>=3 || daily.add(maximumCost).compareTo(BigDecimal.ONE)>0) throw new CoachLimitException("Coach daily reservation limit reached.",Math.max(1,Duration.between(now,day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).toSeconds()));
        jdbc.update("UPDATE coach_daily_usage SET attempts=attempts+1,reserved_usd=reserved_usd+? WHERE quota_day=? AND (scope='GLOBAL' OR owner_id=?)",maximumCost,day,owner);
        jdbc.update("UPDATE coach_requests SET state='RESERVED',reserved_usd=? WHERE owner_id=? AND submission_id=?",maximumCost,owner,key);
    }
    public void processing(UUID owner,UUID key) {
        Instant now=lock();require(owner,key,State.RESERVED);
        jdbc.update("UPDATE coach_requests SET state='PROCESSING',dispatched_at=? WHERE owner_id=? AND submission_id=?",ts(now),owner,key);
    }
    public void complete(UUID owner,UUID key) {
        Instant now=lock();require(owner,key,State.PROCESSING);
        jdbc.update("UPDATE coach_requests SET state='COMPLETED',finished_at=? WHERE owner_id=? AND submission_id=?",ts(now),owner,key);
    }
    public void failBeforeDispatch(UUID owner,UUID key) {
        Instant now=lock();var r=find(owner,key);
        if(r==null) throw new ApiException(404,"Coach request not found.");
        if(r.state()!=State.ACCEPTED && r.state()!=State.RESERVED) throw new ApiException(409,"Coach request cannot transition.");
        // Conservative reservation accounting: no refunds, including pre-dispatch abandonment.
        jdbc.update("UPDATE coach_requests SET state='FAILED_PRE_DISPATCH',finished_at=? WHERE owner_id=? AND submission_id=?",ts(now),owner,key);
    }
    public void finish(UUID owner,UUID key,String json,Instant prepared,Instant expires,boolean rulesOnly) {
        Instant now=lock();require(owner,key,rulesOnly ? State.FAILED_PRE_DISPATCH : State.PROCESSING);
        if(!rulesOnly) jdbc.update("UPDATE coach_requests SET state='COMPLETED',finished_at=? WHERE owner_id=? AND submission_id=?",ts(now),owner,key);
        jdbc.update("INSERT INTO coach_results(owner_id,submission_id,result_json,created_at,expires_at) VALUES(?,?,?,?,?)",owner,key,json,ts(prepared),ts(expires));
    }
    public void uncertain(UUID owner,UUID key) {
        Instant now=lock();var r=find(owner,key);
        if(r==null) throw new ApiException(404,"Coach request not found.");
        if(r.state()==State.OUTCOME_UNKNOWN) return;
        require(owner,key,State.PROCESSING);
        jdbc.update("UPDATE coach_requests SET state='OUTCOME_UNKNOWN',finished_at=? WHERE owner_id=? AND submission_id=?",ts(now),owner,key);
    }
    public Request inspect(UUID owner,UUID key) { lock();var r=find(owner,key);if(r==null) throw new ApiException(404,"Coach request not found.");return r; }
    private void require(UUID owner,UUID key,State state) {
        var r=find(owner,key);if(r==null) throw new ApiException(404,"Coach request not found.");
        if(r.state()!=state) throw new ApiException(409,"Coach request cannot transition.");
    }
    private Request find(UUID owner,UUID key) {
        var rows=jdbc.query("SELECT state,fingerprint,replay_until FROM coach_requests WHERE owner_id=? AND submission_id=?",(rs,n)->new Request(State.valueOf(rs.getString(1)),rs.getString(2),rs.getTimestamp(3).toInstant()),owner,key);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private static Timestamp ts(Instant at) { return Timestamp.from(at); }
}
