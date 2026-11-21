package com.belsenai.identity.controller;

import com.belsenai.identity.dto.*;
import com.belsenai.identity.security.LoginThrottle;
import com.belsenai.identity.security.RegistrationPolicy;
import com.belsenai.identity.security.SessionLogin;
import com.belsenai.identity.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AccountService accounts;
    private final SessionLogin sessions;
    private final LoginThrottle throttle;
    private final RegistrationPolicy registration;

    public AuthController(AccountService accounts, SessionLogin sessions, LoginThrottle throttle, RegistrationPolicy registration) {
        this.registration = registration;
        this.accounts = accounts;
        this.sessions = sessions;
        this.throttle = throttle;
    }

    @GetMapping("/policy")
    public PolicyResponse policy(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return new PolicyResponse(registration.enabled());
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        registration.requireEnabled();
        throttle.check(input.email(), request.getRemoteAddr());
        UserResponse user = accounts.register(input);
        sessions.establish(user.id(), request, response);
        return user;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        throttle.check(input.email(), request.getRemoteAddr());
        UserResponse user = accounts.login(input);
        sessions.establish(user.id(), request, response);
        return user;
    }

    public record PolicyResponse(boolean registrationEnabled) { }
    public record CsrfResponse(String headerName, String token) { }
}
