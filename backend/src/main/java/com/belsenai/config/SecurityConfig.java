package com.belsenai.config;

import java.util.List;
import com.belsenai.common.error.ProblemResponses;
import com.belsenai.identity.security.GoogleOidcUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

    @Bean
    CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponses problems,
            CsrfTokenRepository csrf, SecurityContextRepository contexts, SessionAuthenticationStrategy strategy,
            ObjectProvider<ClientRegistrationRepository> clients, ObjectProvider<GoogleOidcUserService> oidc,
            @Value("${belsen.google.enabled:false}") boolean googleEnabled) throws Exception {
        if (googleEnabled) { clients.getObject(); }
        http.authorizeHttpRequests(authorize -> {
            authorize
                .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/me").authenticated();
            if (googleEnabled) {
                authorize.requestMatchers("/oauth2/authorization/google", "/login/oauth2/code/google").permitAll();
            }
            authorize.anyRequest().denyAll();
        });
        http.csrf(config -> config.csrfTokenRepository(csrf));
        http.securityContext(config -> config.securityContextRepository(contexts).requireExplicitSave(true));
        http.sessionManagement(config -> config.sessionAuthenticationStrategy(strategy));
        http.requestCache(config -> config.requestCache(new NullRequestCache()));
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.logout(config -> config.logoutUrl("/api/v1/auth/logout")
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("BELSEN_SESSION")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                        problems.write(request, response, 401, "Authentication is required."))
                .accessDeniedHandler((request, response, exception) ->
                        problems.write(request, response, 403, "Access is denied.")));
        if (googleEnabled) {
            http.oauth2Login(config -> config.userInfoEndpoint(info -> info.oidcUserService(oidc.getObject()))
                    .successHandler((request, response, authentication) -> response.sendRedirect("/api/v1/me"))
                    .failureHandler((request, response, exception) -> problems.write(request, response, 401,
                            "Google sign-in could not be completed. Use your original sign-in method if you have an account.")));
        }
        return http.build();
    }
}
