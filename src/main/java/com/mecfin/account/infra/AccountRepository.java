package com.mecfin.account.infra;

import com.mecfin.account.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByHouseholdIdAndDeletedAtIsNullOrderByNameAsc(UUID householdId);

    // household_id faz parte do filtro (não só do "where exists"): garante que um id de
    // conta de outro household nunca é encontrado, mesmo que adivinhado (mitigação IDOR/BOLA).
    Optional<Account> findByIdAndHouseholdIdAndDeletedAtIsNull(UUID id, UUID householdId);
}
