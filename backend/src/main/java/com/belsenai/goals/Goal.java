package com.belsenai.goals;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="goals")
public class Goal {
    @Id UUID id;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false, length=200) String title;
    @Column(length=2000) String description;
    @Column(nullable=false, precision=19, scale=4) BigDecimal target;
    @Column(nullable=false, length=50) String unit;
    LocalDate startDate;
    LocalDate deadline;
    Instant archivedAt;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    @Version long version;
    protected Goal() { }
}
