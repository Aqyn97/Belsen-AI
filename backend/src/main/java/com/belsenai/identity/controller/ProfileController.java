package com.belsenai.identity.controller;

import java.util.UUID;
import com.belsenai.identity.dto.*;
import com.belsenai.identity.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final AccountService accounts;

    public ProfileController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public UserResponse profile(Authentication authentication) {
        return accounts.profile(UUID.fromString(authentication.getName()));
    }

    @PatchMapping
    public UserResponse update(Authentication authentication, @Valid @RequestBody ProfileRequest input) {
        return accounts.update(UUID.fromString(authentication.getName()), input);
    }
}
