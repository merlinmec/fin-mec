package com.mecfin.transaction.infra;

import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // accountIds já vem pré-filtrado pelo household (AccountService.householdAccountIds()) -
    // transaction não tem household_id proprio, ver Transaction.java. Os demais filtros são
    // opcionais (":param IS NULL" pula o predicado quando o chamador não informa).
    //
    // competenceMonth usa CAST(:param AS date) mesmo no "IS NULL": sem o cast, esse parâmetro
    // aparece numa posição ($N) cujo único uso sintático é "? IS NULL", que sozinho não dá ao
    // Postgres contexto pra inferir o tipo (DATE) do parâmetro - falha em runtime com
    // "could not determine data type of parameter $N", mesmo com o valor não-nulo. Os demais
    // filtros (UUID/enum) não precisam do cast pelo mesmo motivo.
    @Query(value = "SELECT t FROM Transaction t WHERE t.accountId IN :accountIds "
                    + "AND (:accountId IS NULL OR t.accountId = :accountId) "
                    + "AND (:categoryId IS NULL OR t.categoryId = :categoryId) "
                    + "AND (:type IS NULL OR t.type = :type) "
                    + "AND (:status IS NULL OR t.status = :status) "
                    + "AND (CAST(:competenceMonth AS date) IS NULL OR t.competenceMonth = :competenceMonth) "
                    + "ORDER BY t.transactionDate DESC, t.createdAt DESC",
            countQuery = "SELECT COUNT(t) FROM Transaction t WHERE t.accountId IN :accountIds "
                    + "AND (:accountId IS NULL OR t.accountId = :accountId) "
                    + "AND (:categoryId IS NULL OR t.categoryId = :categoryId) "
                    + "AND (:type IS NULL OR t.type = :type) "
                    + "AND (:status IS NULL OR t.status = :status) "
                    + "AND (CAST(:competenceMonth AS date) IS NULL OR t.competenceMonth = :competenceMonth)")
    Page<Transaction> search(
            @Param("accountIds") List<UUID> accountIds,
            @Param("accountId") UUID accountId,
            @Param("categoryId") UUID categoryId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("competenceMonth") LocalDate competenceMonth,
            Pageable pageable);

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

    // Mesma soma de sumAmount, mas sem filtro de categoria - usado por DashboardService pro
    // total de receitas/despesas do mês (não interessa quebrar por categoria aqui).
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.accountId IN :accountIds "
            + "AND t.competenceMonth = :competenceMonth AND t.status = :status AND t.type = :type")
    BigDecimal sumAmountByMonth(
            @Param("accountIds") List<UUID> accountIds,
            @Param("competenceMonth") LocalDate competenceMonth,
            @Param("status") TransactionStatus status,
            @Param("type") TransactionType type);

    // "Gastos por categoria" do DashboardService - agrupado, exclui lançamentos sem categoria
    // (categoryId nulo não aparece em nenhum grupo).
    @Query("SELECT t.categoryId AS categoryId, COALESCE(SUM(t.amount), 0) AS total FROM Transaction t "
            + "WHERE t.accountId IN :accountIds AND t.categoryId IS NOT NULL "
            + "AND t.competenceMonth = :competenceMonth AND t.status = :status AND t.type = :type "
            + "GROUP BY t.categoryId")
    List<CategoryAmountProjection> sumGroupedByCategory(
            @Param("accountIds") List<UUID> accountIds,
            @Param("competenceMonth") LocalDate competenceMonth,
            @Param("status") TransactionStatus status,
            @Param("type") TransactionType type);

    // Saldo por conta (contábil quando asOfDate=null, disponível quando asOfDate=hoje - ver
    // DashboardService): soma assinada de todo lançamento POSTED, INCOME/IN soma, EXPENSE/OUT
    // subtrai. CAST(:asOfDate AS date) mesmo motivo de search() acima (Postgres não infere o
    // tipo de um parâmetro usado só em "IS NULL").
    @Query("SELECT t.accountId AS accountId, COALESCE(SUM(CASE "
            + "WHEN t.type = com.mecfin.transaction.domain.TransactionType.INCOME THEN t.amount "
            + "WHEN t.type = com.mecfin.transaction.domain.TransactionType.EXPENSE THEN -t.amount "
            + "WHEN t.transferDirection = com.mecfin.transaction.domain.TransactionDirection.IN THEN t.amount "
            + "WHEN t.transferDirection = com.mecfin.transaction.domain.TransactionDirection.OUT THEN -t.amount "
            + "ELSE 0 END), 0) AS total "
            + "FROM Transaction t WHERE t.accountId IN :accountIds AND t.status = :status "
            + "AND (CAST(:asOfDate AS date) IS NULL OR t.transactionDate <= :asOfDate) "
            + "GROUP BY t.accountId")
    List<AccountBalanceProjection> sumSignedAmountsByAccount(
            @Param("accountIds") List<UUID> accountIds,
            @Param("status") TransactionStatus status,
            @Param("asOfDate") LocalDate asOfDate);
}
