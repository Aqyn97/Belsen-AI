package com.belsenai.identity.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record AccountPrincipal(UUID userId) implements Principal, Serializable {
    @Override public String getName() { return userId.toString(); }
}
