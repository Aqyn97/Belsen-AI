package com.belsenai.goals;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {
    private final GoalService goals;
    public GoalController(GoalService goals) { this.goals=goals; }
    private UUID owner(Authentication authentication) { return UUID.fromString(authentication.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public GoalDtos.View create(Authentication a,@Valid @RequestBody GoalDtos.Create request) { return goals.create(owner(a),request); }
    @GetMapping
    public GoalDtos.PageResponse<GoalDtos.View> list(Authentication a,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return goals.list(owner(a),page,size); }
    @GetMapping("/{id}") public GoalDtos.View get(Authentication a,@PathVariable UUID id) { return goals.get(owner(a),id); }
    @PatchMapping("/{id}") public GoalDtos.View update(Authentication a,@PathVariable UUID id,
            @Valid @RequestBody GoalDtos.Update request) { return goals.update(owner(a),id,request); }
    @PostMapping("/{id}/archive") public GoalDtos.View archive(Authentication a,@PathVariable UUID id,
            @Valid @RequestBody GoalDtos.Version request) { return goals.archive(owner(a),id,request.version()); }
    @PostMapping("/{id}/progress") public ResponseEntity<GoalDtos.ProgressView> record(Authentication a,@PathVariable UUID id,
            @Valid @RequestBody GoalDtos.Progress request) {
        var result=goals.record(owner(a),id,request);
        return ResponseEntity.status(result.created()?201:200).body(result.entry());
    }
    @GetMapping("/{id}/progress") public GoalDtos.PageResponse<GoalDtos.ProgressView> history(Authentication a,@PathVariable UUID id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return goals.history(owner(a),id,page,size); }
    @PostMapping("/{id}/progress/{entryId}/void") public GoalDtos.ProgressView voidEntry(Authentication a,@PathVariable UUID id,
            @PathVariable UUID entryId) { return goals.voidEntry(owner(a),id,entryId); }
    @PutMapping("/{id}/weekly-plans/{weekStart}") public GoalDtos.PlanView plan(Authentication a,@PathVariable UUID id,
            @PathVariable LocalDate weekStart,@Valid @RequestBody GoalDtos.Plan request) { return goals.plan(owner(a),id,weekStart,request); }
}
