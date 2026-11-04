package com.belsenai.habits;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="habits")
public class Habit {
    @Id UUID id;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false, length=200) String title;
    @Column(length=2000) String description;
    Instant archivedAt;
    LocalDate archiveEffectiveDate;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    @Version long version;
    @Column(nullable=false) long revisionSequence;
    protected Habit() { }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getArchivedAt() { return archivedAt; }
    public LocalDate getArchiveEffectiveDate() { return archiveEffectiveDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public long getRevisionSequence() { return revisionSequence; }
}
