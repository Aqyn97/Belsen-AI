package com.belsenai.tasks;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name="task_events")
public class TaskEvent {
    @Id UUID id;
    @Column(nullable=false,updatable=false) UUID taskId;
    @Column(nullable=false,updatable=false) UUID ownerId;
    @Column(nullable=false,updatable=false) long sequence;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30,updatable=false) TaskEventType type;
    @Column(nullable=false,updatable=false) Instant occurredAt;
    @Column(nullable=false,updatable=false) UUID reportingWeekId;
    @Column(updatable=false) UUID originalReportingWeekId;
    @Column(updatable=false) LocalDate originalDueDate;
    @Column(updatable=false) LocalDate currentDueDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) TaskStatus status;
    @Column(updatable=false) Instant completedAt;
    @Column(updatable=false) Instant archivedAt;
    protected TaskEvent() { }
}
