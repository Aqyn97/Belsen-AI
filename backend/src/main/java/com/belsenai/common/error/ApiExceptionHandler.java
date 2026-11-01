package com.belsenai.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ProblemResponses problems;

    public ApiExceptionHandler(ProblemResponses problems) { this.problems = problems; }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException exception, HttpServletRequest request) {
        var builder = ResponseEntity.status(exception.status());
        if (exception.status() == 429) { builder.header("Retry-After", "900"); }
        return builder.body(problems.create(exception.status(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = problems.create(400, "Request validation failed.", request.getRequestURI());
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("fieldErrors", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problems.create(400,
                "Malformed JSON or unsupported request fields.", request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(HttpServletRequest request) {
        return ResponseEntity.status(405).body(problems.create(405, "HTTP method is not supported.", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> media(HttpServletRequest request) {
        return ResponseEntity.status(415).body(problems.create(415, "Use application/json.", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
        return ResponseEntity.status(409).body(problems.create(409,
                "Account conflicts with an existing record.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        ProblemDetail problem = problems.create(500, "An unexpected server error occurred.", request.getRequestURI());
        // Do not log exception messages/bodies: they may contain credentials or SQL values.
        LOG.error("Request {} failed ({})", problem.getProperties().get("requestId"), exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(problem);
    }
}
