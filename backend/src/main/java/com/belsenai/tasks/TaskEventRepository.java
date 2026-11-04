package com.belsenai.tasks;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Only insertion is exposed; no update or deletion operation exists. */
public interface TaskEventRepository extends Repository<TaskEvent, UUID> {
    TaskEvent save(TaskEvent event);
}
