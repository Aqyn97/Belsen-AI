package com.belsenai.identity.repository;

import java.util.Optional;
import java.util.UUID;
import com.belsenai.identity.entity.ExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
    Optional<ExternalIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
