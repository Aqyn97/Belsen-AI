package com.belsenai.coach;

/** Safe admission feedback; independent of the login throttle's retry policy. */
public class CoachLimitException extends RuntimeException {
    private final long retryAfter;
    public CoachLimitException(String message,long retryAfter) { super(message);this.retryAfter=retryAfter; }
    public long retryAfter() { return retryAfter; }
}
