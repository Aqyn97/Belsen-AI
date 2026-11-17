package com.belsenai.coach;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.belsenai.common.error.ApiException;

@RestController
@RequestMapping("/api/v1/coach")
public class CoachController {
    public record Status(int schemaVersion,String status,int noticeVersion,int goalLimit) { }
    private final CoachEvidence evidence;
    private final CoachRequests requests;
    public CoachController(CoachEvidence evidence,CoachRequests requests) { this.evidence=evidence;this.requests=requests; }
    @GetMapping("/status")
    public ResponseEntity<Status> status() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(1,"NOT_CONFIGURED",1,20)); }
    /** Explicit preparation only: no analysis, reservation, generated text or provider dispatch. */
    @PostMapping("/evidence")
    public ResponseEntity<CoachEvidence.Snapshot> evidence(Authentication auth,@Valid @RequestBody CoachSelection request) {
        request.validate();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(evidence.prepare(UUID.fromString(auth.getName()),request.weekStart(),request.cutoff()));
    }
    @PostMapping("/analyses")
    public void analyse(Authentication auth,@Valid @RequestBody CoachSelection request) {
        request.validate();
        UUID owner=UUID.fromString(auth.getName());
        evidence.prepare(owner,request.weekStart(),request.cutoff());
        var claim=requests.admit(owner,request.submissionId(),request.fingerprint());
        if(claim.created()) {
            requests.failBeforeDispatch(owner,request.submissionId());
        } else if(claim.request().state()!=CoachRequests.State.FAILED_PRE_DISPATCH) {
            throw new ApiException(409,claim.request().state()==CoachRequests.State.OUTCOME_UNKNOWN
                    ? "Prior analysis outcome is uncertain. No retry was dispatched." : "Analysis request is already tracked. No retry was dispatched.");
        }
        throw new ApiException(503,"AI Coach is not configured. No provider request was made.");
    }
}
