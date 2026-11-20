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
    private final CoachAnalysis analysis;
    private final CoachProviderConfig.Settings settings;
    public CoachController(CoachEvidence evidence,CoachAnalysis analysis,CoachProviderConfig.Settings settings) { this.evidence=evidence;this.analysis=analysis;this.settings=settings; }
    @GetMapping("/status")
    public ResponseEntity<Status> status() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(1,settings.enabled ? "AVAILABLE" : "NOT_CONFIGURED",1,20)); }
    /** Explicit preparation only: no analysis, reservation, generated text or provider dispatch. */
    @PostMapping("/evidence")
    public ResponseEntity<CoachEvidence.Snapshot> evidence(Authentication auth,@Valid @RequestBody CoachSelection request) {
        request.validate();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(evidence.prepare(UUID.fromString(auth.getName()),request.weekStart(),request.cutoff()));
    }
    @PostMapping("/analyses")
    public ResponseEntity<CoachResult> analyse(Authentication auth,@Valid @RequestBody CoachSelection request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analysis.analyse(UUID.fromString(auth.getName()),request));
    }
}
