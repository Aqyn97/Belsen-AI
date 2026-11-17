package com.belsenai.coach;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import jakarta.validation.constraints.*;
import com.belsenai.analytics.DashboardRules;
import com.belsenai.common.error.ApiException;

/** Null means relative current period, not an alias for a particular explicit Monday. */
public record CoachSelection(@NotNull UUID submissionId,LocalDate weekStart,Instant cutoff,
        @NotNull @Min(1) @Max(1) Integer noticeVersion) {
    public void validate() {
        if(submissionId==null || !Integer.valueOf(1).equals(noticeVersion)) throw new ApiException(400,"Invalid Coach request.");
        if(weekStart!=null) DashboardRules.week(weekStart);
        if(cutoff!=null && weekStart==null) throw new ApiException(400,"weekStart is required with cutoff.");
    }
    public String fingerprint() {
        validate();
        String canonical="coach-v1\n"+weekStart+"\n"+cutoff+"\n"+noticeVersion;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
    }
}
