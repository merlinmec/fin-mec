package com.mecfin.bankprovider.infra;

import com.mecfin.bankprovider.domain.BankAccountLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountLinkRepository extends JpaRepository<BankAccountLink, UUID> {

    List<BankAccountLink> findAllByBankConnectionIdOrderByNameAsc(UUID bankConnectionId);

    Optional<BankAccountLink> findByIdAndBankConnectionIdAndHouseholdId(UUID id, UUID bankConnectionId, UUID householdId);

    Optional<BankAccountLink> findByBankConnectionIdAndExternalAccountId(UUID bankConnectionId, String externalAccountId);

    boolean existsByAccountIdAndIdNot(UUID accountId, UUID id);
}
