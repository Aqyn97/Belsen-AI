package com.belsenai.identity.security;

import com.belsenai.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One server-owned switch for new password and Google accounts; existing sign-in is unchanged. */
@Component
public class RegistrationPolicy {
    private final boolean enabled;
    public RegistrationPolicy(@Value("${belsen.registration.enabled:false}") boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public void requireEnabled() {
        if (!enabled) throw new ApiException(403, "Self-registration is disabled. Request individual preview access from the project owner.");
    }
}
