package com.mecfin.category.infra;

import com.mecfin.category.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // "Visível" = categoria do próprio household OU categoria padrão do sistema (household_id nulo).
    // Usado para leitura (list/get); nunca para update/delete, que exigem posse exclusiva.
    @Query("SELECT c FROM Category c WHERE (c.householdId = :householdId OR c.householdId IS NULL) "
            + "AND c.deletedAt IS NULL ORDER BY c.name ASC")
    List<Category> findAllVisibleToHousehold(@Param("householdId") UUID householdId);

    @Query("SELECT c FROM Category c WHERE c.id = :id "
            + "AND (c.householdId = :householdId OR c.householdId IS NULL) AND c.deletedAt IS NULL")
    Optional<Category> findVisibleByIdAndHouseholdId(@Param("id") UUID id, @Param("householdId") UUID householdId);

    // household_id faz parte do filtro (não só do "where exists"): garante que uma categoria de
    // outro household — ou uma categoria padrão do sistema (household_id nulo) — nunca é
    // encontrada por esta consulta, usada exclusivamente para update/delete (posse exclusiva).
    Optional<Category> findByIdAndHouseholdIdAndDeletedAtIsNull(UUID id, UUID householdId);
}
