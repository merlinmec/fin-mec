package com.mecfin.goal.api;

import com.mecfin.goal.application.GoalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public List<GoalResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return goalService.list(includeArchived).stream().map(GoalResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request) {
        return GoalResponse.from(goalService.create(
                request.name(), request.targetAmount(), request.targetDate(), request.color(), request.icon()));
    }

    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable UUID id) {
        return GoalResponse.from(goalService.get(id));
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return GoalResponse.from(goalService.update(id, request.name(), request.targetAmount(), request.targetDate(),
                request.color(), request.icon(), request.archived()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        goalService.delete(id);
    }

    @GetMapping("/{id}/contributions")
    public List<GoalContributionResponse> contributions(@PathVariable UUID id) {
        return goalService.contributions(id).stream().map(GoalContributionResponse::from).toList();
    }

    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse contribute(@PathVariable UUID id, @Valid @RequestBody ContributionRequest request) {
        return GoalResponse.from(goalService.contribute(id, request.amount(), request.date(), request.note()));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public GoalResponse removeContribution(@PathVariable UUID id, @PathVariable UUID contributionId) {
        return GoalResponse.from(goalService.removeContribution(id, contributionId));
    }
}
