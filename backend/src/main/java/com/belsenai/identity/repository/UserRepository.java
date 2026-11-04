package com.belsenai.identity.repository;

import java.util.Optional;
import java.util.UUID;
import com.belsenai.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id=:id")
    Optional<User> lockReportingOwner(UUID id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
