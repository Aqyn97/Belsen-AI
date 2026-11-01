package com.belsenai.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Verified external subject associated with one local account. */
@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ExternalIdentity(User user, String provider, String subject) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerSubject = subject;
        this.createdAt = Instant.now();
    }

    public User getUser() { return user; }

    protected ExternalIdentity() {
    }
}
