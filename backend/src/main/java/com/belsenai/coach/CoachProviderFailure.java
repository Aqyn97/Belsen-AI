package com.belsenai.coach;

/** No upstream body, credential, URI or exception cause may enter this type. */
public class CoachProviderFailure extends RuntimeException {
    private final int status;
    public CoachProviderFailure(int status) { super("AI ranking is unavailable. No automatic retry was made.");this.status=status; }
    public int status() { return status; }
}
