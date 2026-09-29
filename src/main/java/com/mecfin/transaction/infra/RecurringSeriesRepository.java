package com.mecfin.transaction.infra;

import com.mecfin.transaction.domain.RecurringSeries;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringSeriesRepository extends JpaRepository<RecurringSeries, UUID> {

    List<RecurringSeries> findAllByHouseholdIdOrderByCreatedAtDesc(UUID householdId);

    Optional<RecurringSeries> findByIdAndHouseholdId(UUID id, UUID householdId);

    // Usado pelo job diário, que roda fora de qualquer requisição (sem CurrentUser) e
    // atravessa todos os households.
    List<RecurringSeries> findAllByActiveTrue();
}
