package com.mecfin.bankprovider.infra;

import com.mecfin.bankprovider.domain.BankInstitution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankInstitutionRepository extends JpaRepository<BankInstitution, UUID> {

    // Usado por um futuro sync de catálogo (BankProviderClient.listInstitutions) para decidir
    // se uma instituição já existe antes de criar duplicata - providerCode é a chave natural
    // do provedor externo, ver uq_bank_institutions_provider_code.
    Optional<BankInstitution> findByProviderCode(String providerCode);
}
