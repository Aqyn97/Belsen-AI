package com.belsenai.goals;

import java.util.*;
import org.springframework.data.repository.Repository;

public interface ReportingTimezoneRevisionRepository extends Repository<ReportingTimezoneRevision, UUID> {
    ReportingTimezoneRevision save(ReportingTimezoneRevision value);
    List<ReportingTimezoneRevision> findByOwnerIdOrderBySequenceAsc(UUID ownerId);
}
