package com.belsenai.coach;

import java.time.*;
import java.util.*;
import com.belsenai.common.error.ApiException;
import org.springframework.stereotype.Service;

/** Deliberately nontransactional; every ledger/snapshot operation has a separate bounded transaction. */
@Service
public class CoachAnalysis {
    private final CoachRequests requests;
    private final CoachResults results;
    private final CoachEvidence evidence;
    private final CoachProvider provider;
    private final CoachProviderConfig.Settings settings;
    private final Clock clock;
    public CoachAnalysis(CoachRequests requests,CoachResults results,CoachEvidence evidence,CoachProvider provider,CoachProviderConfig.Settings settings,Clock clock) {
        this.requests=requests;this.results=results;this.evidence=evidence;this.provider=provider;this.settings=settings;this.clock=clock;
    }
    public CoachResult analyse(UUID owner,CoachSelection selection) {
        selection.validate();
        // Preserve B1 disabled behavior; no provider or reservation can be reached.
        if(!settings.enabled) {
            evidence.prepare(owner,selection.weekStart(),selection.cutoff());
            var claim=requests.admit(owner,selection.submissionId(),selection.fingerprint());
            if(claim.created()) requests.failBeforeDispatch(owner,selection.submissionId());
            throw new ApiException(503,"AI Coach is not configured. No provider request was made.");
        }
        var claim=requests.admit(owner,selection.submissionId(),selection.fingerprint());
        if(!claim.created()) {
            var replay=results.find(owner,selection.submissionId());
            if(!clock.instant().isBefore(claim.request().replayUntil())) throw new ApiException(410,"Request replay has expired. Use a new submission identifier.");
            if(replay!=null) return replay;
            throw new ApiException(409,claim.request().state()==CoachRequests.State.OUTCOME_UNKNOWN ? "Prior provider outcome is uncertain. No retry was dispatched." : "Analysis is already tracked. No retry was dispatched.");
        }
        boolean dispatched=false;
        try {
            var snapshot=evidence.prepare(owner,selection.weekStart(),selection.cutoff());
            Instant prepared=clock.instant();
            List<String> ids;
            String source;
            if(snapshot.candidates().isEmpty()) { ids=List.of();source="RULES_ONLY";requests.failBeforeDispatch(owner,selection.submissionId()); }
            else {
                provider.validate(snapshot);
                requests.reserveFree(owner,selection.submissionId());
                requests.processing(owner,selection.submissionId());dispatched=true;
                ids=provider.rank(snapshot);source="AI_PRIORITIZED";
                // Validate even alternate/test adapters at the service boundary.
                var allowed=snapshot.candidates().stream().map(CoachEvidence.Candidate::id).toList();
                if(ids==null || ids.isEmpty() || ids.size()>3 || new HashSet<>(ids).size()!=ids.size() || !allowed.containsAll(ids)) throw new CoachProviderFailure(502);
            }
            var result=new CoachResult(1,selection.submissionId(),source,prepared,claim.request().replayUntil(),snapshot,ids);
            results.save(owner,result);
            return result;
        } catch(RuntimeException failure) {
            if(dispatched) requests.uncertain(owner,selection.submissionId());
            else {
                var state=requests.inspect(owner,selection.submissionId()).state();
                if(state==CoachRequests.State.ACCEPTED || state==CoachRequests.State.RESERVED) requests.failBeforeDispatch(owner,selection.submissionId());
            }
            throw failure;
        }
    }
}
