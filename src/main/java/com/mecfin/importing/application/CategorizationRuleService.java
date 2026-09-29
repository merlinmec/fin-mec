package com.mecfin.importing.application;

import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.importing.domain.CategorizationRule;
import com.mecfin.importing.infra.CategorizationRuleRepository;
import com.mecfin.shared.exception.ConflictException;
import com.mecfin.shared.exception.NotFoundException;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.tag.application.TagService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategorizationRuleService {

    private final CategorizationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final TagService tagService;

    public CategorizationRuleService(CategorizationRuleRepository ruleRepository, CategoryRepository categoryRepository,
            TagService tagService) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.tagService = tagService;
    }

    public List<CategorizationRule> list() {
        return ruleRepository.findAllByHouseholdIdOrderByPatternAsc(CurrentUser.householdId());
    }

    @Transactional
    public CategorizationRule create(String pattern, UUID categoryId, UUID tagId) {
        UUID householdId = CurrentUser.householdId();
        validateTargets(categoryId, tagId);
        CategorizationRule rule = new CategorizationRule(householdId, pattern, categoryId, tagId);
        if (ruleRepository.existsByHouseholdIdAndPattern(householdId, rule.getPattern())) {
            throw new ConflictException("Já existe uma regra para \"" + rule.getPattern() + "\"");
        }
        return ruleRepository.save(rule);
    }

    @Transactional
    public CategorizationRule update(UUID id, String pattern, UUID categoryId, UUID tagId) {
        CategorizationRule rule = getOwnedOrThrow(id);
        validateTargets(categoryId, tagId);
        rule.update(pattern, categoryId, tagId);
        if (ruleRepository.existsByHouseholdIdAndPatternAndIdNot(CurrentUser.householdId(), rule.getPattern(), id)) {
            throw new ConflictException("Já existe uma regra para \"" + rule.getPattern() + "\"");
        }
        return rule;
    }

    @Transactional
    public void delete(UUID id) {
        ruleRepository.delete(getOwnedOrThrow(id));
    }

    /** Regra mais específica (padrão mais longo) que casa com a descrição normalizada. */
    public static Optional<CategorizationRule> bestMatch(List<CategorizationRule> rules, String normalizedDescription) {
        return rules.stream()
                .filter(rule -> rule.matches(normalizedDescription))
                .max(Comparator.comparingInt((CategorizationRule rule) -> rule.getPattern().length()));
    }

    private void validateTargets(UUID categoryId, UUID tagId) {
        categoryRepository.findVisibleByIdAndHouseholdId(categoryId, CurrentUser.householdId())
                .orElseThrow(() -> new IllegalArgumentException("categoryId inválido ou não visível: " + categoryId));
        if (tagId != null) {
            tagService.requireOwned(Set.of(tagId));
        }
    }

    private CategorizationRule getOwnedOrThrow(UUID id) {
        return ruleRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new NotFoundException("Regra não encontrada: " + id));
    }
}
