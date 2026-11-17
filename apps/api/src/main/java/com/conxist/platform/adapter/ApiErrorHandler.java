package com.conxist.platform.adapter;

import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Turns failures into problem responses with a stable {@code code} the web app translates, and a plain English
 * {@code detail}. Unexpected failures are logged with the request id and answered with a generic message.
 */
@RestControllerAdvice
class ApiErrorHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException invalid) {
        return problem(HttpStatus.BAD_REQUEST, "request.invalid", "Some fields are missing or not valid.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail fileTooLarge(MaxUploadSizeExceededException tooLarge) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "document.too-large", "This file is too large to upload.");
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

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}
