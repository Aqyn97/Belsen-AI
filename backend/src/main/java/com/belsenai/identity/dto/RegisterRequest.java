package com.belsenai.identity.dto;

import com.belsenai.identity.service.EmailNormalizer;
import com.belsenai.identity.validation.ValidPassword;
import com.belsenai.identity.validation.ValidTimezone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @ValidPassword String password,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @ValidTimezone String timezone) {
    public RegisterRequest {
        email = EmailNormalizer.normalize(email);
        displayName = displayName == null ? null : displayName.strip();
    }

    @Override public String toString() { return "RegisterRequest [redacted]"; }
}
