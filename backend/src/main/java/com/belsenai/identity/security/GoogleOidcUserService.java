package com.belsenai.identity.security;

import com.belsenai.identity.service.GoogleAccountService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final GoogleAccountService accounts;
    private final OidcUserService delegate = new OidcUserService();

    public GoogleOidcUserService(GoogleAccountService accounts) { this.accounts = accounts; }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
            throw failure();
        }
        OidcUser verified = delegate.loadUser(request);
        try {
            return new LocalOidcUser(accounts.resolve(verified.getSubject(), verified.getEmail(),
                    Boolean.TRUE.equals(verified.getEmailVerified()), verified.getFullName()), verified);
        } catch (RuntimeException exception) {
            // Provider claims, persistence details and collision information stay server-side.
            throw failure();
        }
    }

    private OAuth2AuthenticationException failure() {
        return new OAuth2AuthenticationException(new OAuth2Error("account_not_available"),
                "Google sign-in could not be completed. Use your original sign-in method if you have an account.");
    }
}
