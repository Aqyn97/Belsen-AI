package com.belsenai.analytics;

import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import com.belsenai.common.error.ApiException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private static final Set<String> PARAMETERS=Set.of("weekStart","cutoff","goalsPage","goalsSize");
    public DashboardController(DashboardService dashboard) { this.dashboard=dashboard; }
    @GetMapping
    public ResponseEntity<DashboardDtos.Dashboard> get(Authentication authentication,
            @RequestParam(required=false) LocalDate weekStart,@RequestParam(required=false) Instant cutoff,
            @RequestParam(defaultValue="0") int goalsPage,@RequestParam(defaultValue="20") int goalsSize,
            HttpServletRequest request) {
        request.getParameterMap().forEach((key,values) -> {
            if(!PARAMETERS.contains(key) || values.length!=1 || values[0].isBlank()) throw new ApiException(400,"Unsupported, repeated or empty dashboard parameter.");
        });
        var result=dashboard.dashboard(UUID.fromString(authentication.getName()),weekStart,cutoff,goalsPage,goalsSize);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
