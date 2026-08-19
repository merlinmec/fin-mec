package com.mecfin.transaction.infra;

import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // accountIds já vem pré-filtrado pelo household (AccountService.householdAccountIds()) -
    // transaction não tem household_id proprio, ver Transaction.java.
    List<Transaction> findAllByAccountIdInOrderByTransactionDateDescCreatedAtDesc(List<UUID> accountIds);

    List<Transaction> findAllByAccountIdInAndCompetenceMonthOrderByTransactionDateDescCreatedAtDesc(
            List<UUID> accountIds, LocalDate competenceMonth);

    Optional<Transaction> findByIdAndAccountIdIn(UUID id, List<UUID> accountIds);

    // Usado por BudgetService para o gasto realizado: soma só o que efetivamente aconteceu
    // (status/type explícitos no parametro, não fixos aqui, para o chamador decidir a regra).
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.accountId IN :accountIds "
            + "AND t.categoryId = :categoryId AND t.competenceMonth = :competenceMonth "
            + "AND t.status = :status AND t.type = :type")
    BigDecimal sumAmount(
            @Param("accountIds") List<UUID> accountIds,
            @Param("categoryId") UUID categoryId,
            @Param("competenceMonth") LocalDate competenceMonth,
            @Param("status") TransactionStatus status,
            @Param("type") TransactionType type);
}
