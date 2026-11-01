package com.belsenai.common.error;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemResponses {
    private final ObjectMapper mapper;

    public ProblemResponses(ObjectMapper mapper) { this.mapper = mapper; }

    public ProblemDetail create(int status, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setInstance(URI.create(path));
        problem.setProperty("requestId", UUID.randomUUID().toString());
        return problem;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
            int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Explicit fields keep filter errors identical to MVC Problem Details.
        ProblemDetail problem = create(status, detail, request.getRequestURI());
        mapper.writeValue(response.getWriter(), Map.of("type", "about:blank", "title", problem.getTitle(),
                "status", status, "detail", detail, "instance", problem.getInstance().toString(),
                "requestId", problem.getProperties().get("requestId")));
    }
}
