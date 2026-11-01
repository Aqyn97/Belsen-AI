package com.belsenai.identity.dto;

import java.util.UUID;
import com.belsenai.identity.entity.User;

public record UserResponse(UUID id, String email, String displayName, String timezone) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimezone());
    }
}
