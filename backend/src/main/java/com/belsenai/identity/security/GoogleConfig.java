package com.belsenai.identity.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.validation.annotation.Validated;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "belsen.google.enabled", havingValue = "true")
@EnableConfigurationProperties(GoogleConfig.Settings.class)
public class GoogleConfig {
    @Validated
    @ConfigurationProperties("belsen.google")
    public record Settings(@NotBlank String clientId, @NotBlank String clientSecret) {
        @Override public String toString() { return "Google settings [redacted]"; }
    }

    @Bean
    ClientRegistrationRepository googleClients(Settings settings, @Value("${belsen.google.redirect-uri:}") String redirectUri) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
                .clientId(settings.clientId()).clientSecret(settings.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri.isBlank() ? "{baseUrl}/login/oauth2/code/{registrationId}" : redirectUri)
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .clientName("Google").build());
    }
}
