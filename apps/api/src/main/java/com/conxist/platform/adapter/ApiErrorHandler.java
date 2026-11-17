package com.conxist.platform.adapter;

import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Turns rule breaks into plain-language problem responses the canvas can show as they are. */
@RestControllerAdvice
class ApiErrorHandler {

    static final String FILE_TOO_LARGE = "This file is too large to upload.";
    static final String INVALID_REQUEST = "Some fields are missing or not valid.";

    @ExceptionHandler(RuleViolation.class)
    ProblemDetail ruleViolation(RuleViolation violation) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, violation.getMessage());
    }

    @ExceptionHandler(NotFound.class)
    ProblemDetail notFound(NotFound missing) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, missing.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException invalid) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail fileTooLarge(MaxUploadSizeExceededException tooLarge) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, FILE_TOO_LARGE);
    }
}
