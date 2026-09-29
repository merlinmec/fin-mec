package com.mecfin.importing.infra;

import com.mecfin.importing.domain.ImportBatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    List<ImportBatch> findTop30ByHouseholdIdOrderByCreatedAtDesc(UUID householdId);

    Optional<ImportBatch> findByIdAndHouseholdId(UUID id, UUID householdId);
}
