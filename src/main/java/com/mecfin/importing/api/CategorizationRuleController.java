package com.mecfin.importing.api;

import com.mecfin.importing.application.CategorizationRuleService;
import com.mecfin.importing.domain.CategorizationRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categorization-rules")
public class CategorizationRuleController {

    public record RuleRequest(@NotBlank @Size(max = 100) String pattern, @NotNull UUID categoryId, UUID tagId) {
    }

    public record RuleResponse(UUID id, String pattern, UUID categoryId, UUID tagId, Instant createdAt) {

        static RuleResponse from(CategorizationRule rule) {
            return new RuleResponse(rule.getId(), rule.getPattern(), rule.getCategoryId(), rule.getTagId(),
                    rule.getCreatedAt());
        }
    }

    private final CategorizationRuleService ruleService;

    public CategorizationRuleController(CategorizationRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<RuleResponse> list() {
        return ruleService.list().stream().map(RuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(ruleService.create(request.pattern(), request.categoryId(), request.tagId()));
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable UUID id, @Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(ruleService.update(id, request.pattern(), request.categoryId(), request.tagId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        ruleService.delete(id);
    }
}
