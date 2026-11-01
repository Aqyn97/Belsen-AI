package com.belsenai.identity.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class LocalOidcUser extends DefaultOidcUser {
    private final UUID userId;

    public LocalOidcUser(UUID userId, OidcUser verified) {
        super(verified.getAuthorities(), verified.getIdToken(), verified.getUserInfo());
        this.userId = userId;
    }

    @Override public String getName() { return userId.toString(); }
}
