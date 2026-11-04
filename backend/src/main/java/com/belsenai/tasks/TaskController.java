package com.belsenai.tasks;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService tasks;
    public TaskController(TaskService tasks) { this.tasks=tasks; }
    private UUID owner(Authentication a) { return UUID.fromString(a.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TaskDtos.View create(Authentication a,@Valid @RequestBody TaskDtos.Create request) { return tasks.create(owner(a),request); }
    @GetMapping public TaskDtos.PageView list(Authentication a,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam(required=false) TaskStatus status,
            @RequestParam(defaultValue="false") boolean archived,@RequestParam(required=false) UUID goalId,
            @RequestParam(required=false) LocalDate dueDate,@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to) { return tasks.list(owner(a),page,size,status,archived,goalId,dueDate,from,to); }
    @GetMapping("/{id}") public TaskDtos.View get(Authentication a,@PathVariable UUID id) { return tasks.get(owner(a),id); }
    @PatchMapping("/{id}") public TaskDtos.View update(Authentication a,@PathVariable UUID id,@Valid @RequestBody TaskDtos.Update request) { return tasks.update(owner(a),id,request); }
    @PostMapping("/{id}/complete") public TaskDtos.View complete(Authentication a,@PathVariable UUID id,@Valid @RequestBody TaskDtos.Version request) { return tasks.complete(owner(a),id,request.version()); }
    @PostMapping("/{id}/reopen") public TaskDtos.View reopen(Authentication a,@PathVariable UUID id,@Valid @RequestBody TaskDtos.Version request) { return tasks.reopen(owner(a),id,request.version()); }
    @PostMapping("/{id}/reschedule") public TaskDtos.View reschedule(Authentication a,@PathVariable UUID id,@Valid @RequestBody TaskDtos.Reschedule request) { return tasks.reschedule(owner(a),id,request); }
    @PostMapping("/{id}/archive") public TaskDtos.View archive(Authentication a,@PathVariable UUID id,@Valid @RequestBody TaskDtos.Version request) { return tasks.archive(owner(a),id,request.version()); }
}
