package com.belsenai.identity.dto;

import com.belsenai.identity.validation.ValidTimezone;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileRequest(@Size(min = 1, max = 100) @Pattern(regexp = "(?s).*\\S.*") String displayName,
        @ValidTimezone String timezone) {
    public ProfileRequest { displayName = displayName == null ? null : displayName.strip(); }
}
