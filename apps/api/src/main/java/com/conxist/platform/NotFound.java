package com.conxist.platform;

/** The requested resource does not exist in the current workspace. */
public class NotFound extends RuntimeException {

    public NotFound(String userMessage) {
        super(userMessage);
    }
}
