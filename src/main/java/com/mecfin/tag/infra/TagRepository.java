package com.mecfin.tag.infra;

import com.mecfin.tag.domain.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByHouseholdIdOrderByNameAsc(UUID householdId);

    Optional<Tag> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<Tag> findAllByIdInAndHouseholdId(Collection<UUID> ids, UUID householdId);

    // Espelha o índice único ux_tags_household_name (LOWER(name)) - checagem antecipada para
    // devolver 409 com mensagem clara em vez de estourar a constraint.
    @Query("SELECT COUNT(t) > 0 FROM Tag t WHERE t.householdId = :householdId "
            + "AND LOWER(t.name) = LOWER(:name) AND (:excludeId IS NULL OR t.id <> :excludeId)")
    boolean existsByName(
            @Param("householdId") UUID householdId, @Param("name") String name, @Param("excludeId") UUID excludeId);

    // Quantos lançamentos usam cada tag (tela de tags mostra o uso antes de excluir).
    @Query(value = "SELECT CAST(tag_id AS varchar) AS tagId, COUNT(*) AS total FROM transaction_tags "
            + "WHERE tag_id IN (:tagIds) GROUP BY tag_id", nativeQuery = true)
    List<TagUsageProjection> countUsage(@Param("tagIds") Collection<UUID> tagIds);
}
