package com.belsenai.coach;

import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class CoachResults {
    private final JdbcTemplate jdbc;
    private final CoachRequests requests;
    private final ObjectMapper mapper;
    private final Clock clock;
    public CoachResults(JdbcTemplate jdbc,CoachRequests requests,ObjectMapper mapper,Clock clock) { this.jdbc=jdbc;this.requests=requests;this.mapper=mapper;this.clock=clock; }
    public void save(UUID owner,CoachResult result) {
        String json=mapper.writeValueAsString(result);
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768) throw new IllegalStateException("Coach replay size exceeded");
        requests.finish(owner,result.submissionId(),json,result.preparedAt(),result.expiresAt(),result.source().equals("RULES_ONLY"));
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public CoachResult find(UUID owner,UUID key) {
        var rows=jdbc.query("SELECT result_json FROM coach_results WHERE owner_id=? AND submission_id=? AND expires_at>?",(rs,n)->mapper.readValue(rs.getString(1),CoachResult.class),owner,key,java.sql.Timestamp.from(clock.instant()));
        return rows.isEmpty()?null:rows.getFirst();
    }
}
