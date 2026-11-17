package com.belsenai.coach;

import com.belsenai.common.error.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Order(-1)
public class CoachErrors {
    private final ProblemResponses problems;
    public CoachErrors(ProblemResponses problems) { this.problems=problems; }
    @ExceptionHandler(CoachLimitException.class)
    public ResponseEntity<ProblemDetail> limit(CoachLimitException error,HttpServletRequest request) {
        return ResponseEntity.status(429).cacheControl(CacheControl.noStore()).header("Retry-After",Long.toString(error.retryAfter()))
                .body(problems.create(429,error.getMessage(),request.getRequestURI()));
    }
}
