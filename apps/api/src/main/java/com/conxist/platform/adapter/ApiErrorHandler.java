package com.conxist.platform.adapter;

import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns failures into problem responses with a stable {@code code} the web app translates, and a plain English
 * {@code detail}. Requests the client got wrong keep their 4xx status; only unexpected failures are logged with a
 * stack trace (the request id is in every log line).
 */
@RestControllerAdvice
class ApiErrorHandler extends ResponseEntityExceptionHandler {

    static final String CODE = "code";
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(NotFound.class)
    ProblemDetail notFound(NotFound missing) {
        return problem(HttpStatus.NOT_FOUND, missing.code(), missing.getMessage());
    }

    @ExceptionHandler(RuleViolation.class)
    ProblemDetail ruleViolation(RuleViolation violation) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, violation.code(), violation.getMessage());
    }

    /** Two requests raced past a uniqueness check; the database constraint caught the second one. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException conflict) {
        log.warn("Request conflicted with existing data: {}", conflict.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "request.conflict", "This changed at the same time. Try again.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception failure) {
        log.error("Unexpected failure", failure);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "request.failed", "Something went wrong. Try again.");
    }

    /** Spring's own request errors (bad JSON, wrong id format, missing part, unknown path, too large). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        log.info("Request refused with {}: {}", status.value(), failure.getClass().getSimpleName());
        ProblemDetail problem = failure instanceof MaxUploadSizeExceededException
                ? problem(status, "document.too-large", "This file is too large to upload.")
                : status.value() == HttpStatus.NOT_FOUND.value()
                        ? problem(status, "request.not-found", "There is nothing at this address.")
                        : problem(status, "request.invalid", "Some fields are missing or not valid.");
        return super.handleExceptionInternal(failure, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}
