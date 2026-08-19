package com.mecfin.budget.infra;

import com.mecfin.budget.domain.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findAllByHouseholdIdOrderByReferenceMonthDescCreatedAtAsc(UUID householdId);

    List<Budget> findAllByHouseholdIdAndReferenceMonthOrderByCreatedAtAsc(UUID householdId, LocalDate referenceMonth);

    // household_id faz parte do filtro (não só do "where exists"): garante que um id de
    // orçamento de outro household nunca é encontrado, mesmo que adivinhado (mitigação IDOR/BOLA).
    Optional<Budget> findByIdAndHouseholdId(UUID id, UUID householdId);
}
