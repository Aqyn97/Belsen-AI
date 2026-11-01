package com.belsenai.identity.security;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionLogin {
    private final SessionAuthenticationStrategy strategy;
    private final SecurityContextRepository contexts;

    public SessionLogin(SessionAuthenticationStrategy strategy, SecurityContextRepository contexts) {
        this.strategy = strategy;
        this.contexts = contexts;
    }

    public void establish(UUID userId, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal(userId), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        strategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
