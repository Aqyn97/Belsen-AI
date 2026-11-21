package com.belsenai.identity.service;

import java.util.UUID;
import com.belsenai.common.error.ApiException;
import com.belsenai.identity.entity.ExternalIdentity;
import com.belsenai.identity.entity.User;
import com.belsenai.identity.security.RegistrationPolicy;
import com.belsenai.identity.repository.ExternalIdentityRepository;
import com.belsenai.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAccountService {
    private final ExternalIdentityRepository identities;
    private final UserRepository users;
    private final RegistrationPolicy registration;

    public GoogleAccountService(ExternalIdentityRepository identities, UserRepository users, RegistrationPolicy registration) {
        this.registration = registration;
        this.identities = identities;
        this.users = users;
    }

    /** Called only with claims from Spring Security's validated Google OIDC response. */
    @Transactional
    public UUID resolve(String subject, String email, boolean emailVerified, String displayName) {
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new ApiException(401, "Google sign-in could not be completed.");
        }
        var existing = identities.findByProviderAndProviderSubject("google", subject);
        if (existing.isPresent()) { return existing.get().getUser().getId(); }
        registration.requireEnabled();
        String normalized = EmailNormalizer.normalize(email);
        if (!emailVerified || normalized == null || normalized.length() > 254
                || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ApiException(401, "Google sign-in requires a verified email address.");
        }
        if (users.existsByEmail(normalized)) {
            throw new ApiException(409, "This email belongs to an existing account. Sign in using its original method.");
        }
        String name = displayName == null ? "Google user" : displayName.strip();
        if (name.isBlank()) { name = "Google user"; }
        if (name.length() > 100) { name = name.substring(0, 100); }
        User user = users.saveAndFlush(new User(normalized, null, name, "UTC"));
        identities.saveAndFlush(new ExternalIdentity(user, "google", subject));
        return user.getId();
    }
}
