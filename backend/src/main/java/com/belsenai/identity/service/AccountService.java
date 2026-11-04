package com.belsenai.identity.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.belsenai.common.error.ApiException;
import com.belsenai.goals.ReportingCalendar;
import com.belsenai.identity.dto.*;
import com.belsenai.identity.entity.User;
import com.belsenai.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    private final ReportingCalendar calendar;

    public AccountService(UserRepository users, PasswordEncoder passwords, ReportingCalendar calendar) {
        this.users = users;
        this.calendar = calendar;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) { throw new ApiException(409, "Email is already registered."); }
        User user = new User(request.email(), passwords.encode(request.password()), request.displayName(), request.timezone());
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email()).orElse(null);
        String hash = user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash();
        boolean matched = request.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.password(), hash);
        if (!matched || user == null || user.getPasswordHash() == null) {
            throw new ApiException(401, "Invalid email or password.");
        }
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse profile(UUID id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse update(UUID id, ProfileRequest request) {
        if (request.displayName() == null && request.timezone() == null) {
            throw new ApiException(400, "Provide displayName or timezone.");
        }
        User user = calendar.lockOwner(id);
        if(request.timezone()!=null && !request.timezone().equals(user.getTimezone())) {
            calendar.changeTimezonePolicy(user,request.timezone(),calendar.now());
        }
        user.updateProfile(request.displayName(), request.timezone());
        return UserResponse.from(user);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(401, "Account is unavailable."));
    }
}
