package com.mecfin.bankprovider.infra;

import com.mecfin.bankprovider.domain.BankConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankConnectionRepository extends JpaRepository<BankConnection, UUID> {

    List<BankConnection> findAllByHouseholdId(UUID householdId);

    // household_id faz parte do filtro (não só do "where exists"): garante que uma conexão de
    // outro household nunca é encontrada, mesmo que adivinhada (mitigação IDOR/BOLA).
    Optional<BankConnection> findByIdAndHouseholdId(UUID id, UUID householdId);

    // Chave de idempotência de um sync (ver uq_bank_connections_provider_item) - reprocessar a
    // mesma conexão do provedor nunca cria duplicata.
    Optional<BankConnection> findByProviderAndExternalItemId(String provider, String externalItemId);
}
