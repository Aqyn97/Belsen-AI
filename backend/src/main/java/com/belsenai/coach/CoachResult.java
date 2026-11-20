package com.belsenai.coach;

import java.time.Instant;
import java.util.*;

/** Minimal replay record: original numeric evidence and ranked server-defined actions. */
public record CoachResult(int schemaVersion,UUID submissionId,String source,Instant preparedAt,
        Instant expiresAt,CoachEvidence.Snapshot evidence,List<String> selectedIds) {
    public CoachResult { selectedIds=List.copyOf(selectedIds); }
    public List<CoachEvidence.Candidate> recommendations() {
        var byId=new HashMap<String,CoachEvidence.Candidate>();evidence.candidates().forEach(c->byId.put(c.id(),c));
        return selectedIds.stream().map(byId::get).toList();
    }
}
