package com.mecfin.creditcard.infra;

import com.mecfin.creditcard.domain.CreditCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    List<CreditCard> findAllByHouseholdIdAndDeletedAtIsNullOrderByNameAsc(UUID householdId);

    // household_id faz parte do filtro (não só do "where exists"): garante que um id de
    // cartão de outro household nunca é encontrado, mesmo que adivinhado (mitigação IDOR/BOLA).
    Optional<CreditCard> findByIdAndHouseholdIdAndDeletedAtIsNull(UUID id, UUID householdId);

    // Sem filtro de deletedAt (diferente dos dois acima): usado para escopar fatura/cobrança
    // por household via credit_card_id, sem perder o histórico de um cartão já excluído -
    // mesmo padrão de AccountRepository.findAllByHouseholdId.
    List<CreditCard> findAllByHouseholdId(UUID householdId);
}
