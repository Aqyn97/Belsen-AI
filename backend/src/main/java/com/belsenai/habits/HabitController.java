package com.belsenai.habits;

import java.time.*;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/habits")
public class HabitController {
    private final HabitService habits;
    public HabitController(HabitService habits) { this.habits=habits; }
    private UUID owner(Authentication authentication) { return UUID.fromString(authentication.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public HabitDtos.View create(Authentication a,@Valid @RequestBody HabitDtos.Create request) { return habits.create(owner(a),request); }
    @GetMapping
    public HabitDtos.Page<HabitDtos.View> list(Authentication a,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="false") boolean archived) { return habits.list(owner(a),page,size,archived); }
    @GetMapping("/{id}")
    public HabitDtos.View get(Authentication a,@PathVariable UUID id) { return habits.get(owner(a),id); }
    @PatchMapping("/{id}")
    public HabitDtos.View update(Authentication a,@PathVariable UUID id,@Valid @RequestBody HabitDtos.Update request) { return habits.update(owner(a),id,request); }
    @PostMapping("/{id}/schedules")
    public HabitDtos.View schedule(Authentication a,@PathVariable UUID id,@Valid @RequestBody HabitDtos.Schedule request) { return habits.schedule(owner(a),id,request); }
    @PostMapping("/{id}/archive")
    public HabitDtos.View archive(Authentication a,@PathVariable UUID id,@Valid @RequestBody HabitDtos.Version request) { return habits.archive(owner(a),id,request.version()); }
    @PutMapping("/{id}/completions/{date}")
    public HabitDtos.DayView complete(Authentication a,@PathVariable UUID id,@PathVariable LocalDate date,@Valid @RequestBody HabitDtos.Action request) { return habits.complete(owner(a),id,date,request); }
    @DeleteMapping("/{id}/completions/{date}")
    public HabitDtos.DayView undo(Authentication a,@PathVariable UUID id,@PathVariable LocalDate date,
            @RequestParam(required=false) LocalDate weekStart,@RequestHeader(value="If-Match",required=false) String version) {
        return habits.undo(owner(a),id,date,weekStart,HabitRules.ifMatch(version));
    }
    @PutMapping("/{id}/rest-days/{date}")
    public HabitDtos.DayView rest(Authentication a,@PathVariable UUID id,@PathVariable LocalDate date,@Valid @RequestBody HabitDtos.Rest request) { return habits.rest(owner(a),id,date,request); }
    @DeleteMapping("/{id}/rest-days/{date}")
    public HabitDtos.DayView cancelRest(Authentication a,@PathVariable UUID id,@PathVariable LocalDate date,
            @RequestParam(required=false) LocalDate weekStart,@RequestHeader(value="If-Match",required=false) String version) {
        return habits.cancelRest(owner(a),id,date,weekStart,HabitRules.ifMatch(version));
    }
    @GetMapping("/{id}/history")
    public HabitDtos.History history(Authentication a,@PathVariable UUID id,@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,@RequestParam(required=false) Instant cutoff,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="0") int actionPage,@RequestParam(defaultValue="0") int schedulePage) {
        return habits.history(owner(a),id,from,to,cutoff,page,size,actionPage,schedulePage);
    }
}
