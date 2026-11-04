package com.belsenai.tasks;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="tasks")
public class Task {
    @Id UUID id;
    @Column(nullable=false) UUID ownerId;
    UUID goalId;
    @Column(nullable=false,length=200) String title;
    @Column(length=2000) String description;
    LocalDate originalDueDate;
    LocalDate currentDueDate;
    UUID originalReportingWeekId;
    Integer estimatedDurationMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) TaskStatus status;
    Instant completedAt;
    Instant archivedAt;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    @Column(nullable=false) long eventSequence;
    @Version long version;
    protected Task() { }
}
