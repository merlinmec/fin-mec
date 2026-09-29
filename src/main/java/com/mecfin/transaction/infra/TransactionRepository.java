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
    // Parâmetros DATE usam CAST(:param AS date) mesmo no "IS NULL": sem o cast, esse parâmetro
    // aparece numa posição ($N) cujo único uso sintático é "? IS NULL", que sozinho não dá ao
    // Postgres contexto pra inferir o tipo (DATE) do parâmetro - falha em runtime com
    // "could not determine data type of parameter $N", mesmo com o valor não-nulo. UUID, enum,
    // texto e numérico chegam tipados pelo driver JDBC e não precisam do cast.
    //
    // likePattern já vem minúsculo e com os curingas do usuário escapados com "!" (ver
    // TransactionFilter). "!" em vez de "\" de propósito: barra invertida passaria por três
    // camadas de escape (Java, HQL, SQL) e o Hibernate não a trata como escape implícito.
    String SEARCH_PREDICATE = "t.accountId IN :accountIds "
            + "AND (:accountId IS NULL OR t.accountId = :accountId) "
            + "AND (:categoryId IS NULL OR t.categoryId = :categoryId) "
            + "AND (:type IS NULL OR t.type = :type) "
            + "AND (:status IS NULL OR t.status = :status) "
            + "AND (CAST(:competenceMonth AS date) IS NULL OR t.competenceMonth = :competenceMonth) "
            + "AND (CAST(:fromDate AS date) IS NULL OR t.transactionDate >= :fromDate) "
            + "AND (CAST(:toDate AS date) IS NULL OR t.transactionDate <= :toDate) "
            + "AND (:likePattern IS NULL OR LOWER(t.description) LIKE :likePattern ESCAPE '!') "
            + "AND (:minAmount IS NULL OR t.amount >= :minAmount) "
            + "AND (:maxAmount IS NULL OR t.amount <= :maxAmount) "
            + "AND (:tagId IS NULL OR :tagId MEMBER OF t.tagIds)";

    @Query(value = "SELECT t FROM Transaction t WHERE " + SEARCH_PREDICATE
                    + " ORDER BY t.transactionDate DESC, t.createdAt DESC",
            countQuery = "SELECT COUNT(t) FROM Transaction t WHERE " + SEARCH_PREDICATE)
    Page<Transaction> search(
            @Param("accountIds") List<UUID> accountIds,
            @Param("accountId") UUID accountId,
            @Param("categoryId") UUID categoryId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("competenceMonth") LocalDate competenceMonth,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("likePattern") String likePattern,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("tagId") UUID tagId,
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

    // ---- importação de extrato (Fase 15) ----

    @Query("SELECT t.externalId FROM Transaction t WHERE t.accountId = :accountId AND t.externalId IN :externalIds")
    List<String> findExternalIds(@Param("accountId") UUID accountId, @Param("externalIds") List<String> externalIds);

    // Candidatos a duplicado manual / previsto a efetivar na janela de datas do extrato.
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId "
            + "AND t.transactionDate BETWEEN :fromDate AND :toDate "
            + "AND t.status <> com.mecfin.transaction.domain.TransactionStatus.CANCELED "
            + "AND t.type <> com.mecfin.transaction.domain.TransactionType.TRANSFER")
    List<Transaction> findForStatementMatching(
            @Param("accountId") UUID accountId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT t FROM Transaction t WHERE t.accountId IN :accountIds AND t.categoryId IS NOT NULL "
            + "AND t.status <> com.mecfin.transaction.domain.TransactionStatus.CANCELED "
            + "AND t.type <> com.mecfin.transaction.domain.TransactionType.TRANSFER "
            + "ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findRecentCategorized(@Param("accountIds") List<UUID> accountIds, Pageable pageable);

    List<Transaction> findAllByImportBatchId(UUID importBatchId);

    List<Transaction> findAllByRecurrenceSeriesIdAndStatusOrderByRecurrenceIndexAsc(
            UUID recurrenceSeriesId, TransactionStatus status);

    // "Próxima ocorrência" de um fixo = a primeira ainda não efetivada, derivada na leitura.
    @Query("SELECT MIN(t.transactionDate) FROM Transaction t WHERE t.recurrenceSeriesId = :seriesId "
            + "AND t.status = com.mecfin.transaction.domain.TransactionStatus.PENDING")
    LocalDate findNextPendingDate(@Param("seriesId") UUID seriesId);

    // Relatório de fluxo de caixa: receitas/despesas por competência, separando efetivado de
    // previsto. Transferência fica de fora (não é receita nem despesa, só troca de conta).
    @Query("SELECT t.competenceMonth AS month, t.type AS type, t.status AS status, "
            + "COALESCE(SUM(t.amount), 0) AS total FROM Transaction t "
            + "WHERE t.accountId IN :accountIds AND t.competenceMonth BETWEEN :fromMonth AND :toMonth "
            + "AND t.type IN (com.mecfin.transaction.domain.TransactionType.INCOME, "
            + "com.mecfin.transaction.domain.TransactionType.EXPENSE) "
            + "AND t.status IN (com.mecfin.transaction.domain.TransactionStatus.POSTED, "
            + "com.mecfin.transaction.domain.TransactionStatus.PENDING) "
            + "GROUP BY t.competenceMonth, t.type, t.status")
    List<MonthlyTypeTotalProjection> sumByMonthTypeAndStatus(
            @Param("accountIds") List<UUID> accountIds,
            @Param("fromMonth") LocalDate fromMonth,
            @Param("toMonth") LocalDate toMonth);

    // Evolução de saldo: mesma soma assinada de sumSignedAmountsByAccount, mas agrupada pelo
    // mês-calendário da data do lançamento (saldo é sobre quando o dinheiro se moveu, não
    // sobre a competência).
    @Query("SELECT EXTRACT(YEAR FROM t.transactionDate) AS year, EXTRACT(MONTH FROM t.transactionDate) AS month, "
            + "COALESCE(SUM(CASE "
            + "WHEN t.type = com.mecfin.transaction.domain.TransactionType.INCOME THEN t.amount "
            + "WHEN t.type = com.mecfin.transaction.domain.TransactionType.EXPENSE THEN -t.amount "
            + "WHEN t.transferDirection = com.mecfin.transaction.domain.TransactionDirection.IN THEN t.amount "
            + "WHEN t.transferDirection = com.mecfin.transaction.domain.TransactionDirection.OUT THEN -t.amount "
            + "ELSE 0 END), 0) AS total "
            + "FROM Transaction t WHERE t.accountId IN :accountIds "
            + "AND t.status = com.mecfin.transaction.domain.TransactionStatus.POSTED "
            + "AND t.transactionDate BETWEEN :fromDate AND :toDate "
            + "GROUP BY EXTRACT(YEAR FROM t.transactionDate), EXTRACT(MONTH FROM t.transactionDate)")
    List<MonthlyFlowProjection> sumSignedByTransactionMonth(
            @Param("accountIds") List<UUID> accountIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    // Relatório por categoria: efetivado do tipo pedido, por categoria e competência.
    @Query("SELECT t.categoryId AS categoryId, t.competenceMonth AS month, COALESCE(SUM(t.amount), 0) AS total "
            + "FROM Transaction t WHERE t.accountId IN :accountIds "
            + "AND t.competenceMonth BETWEEN :fromMonth AND :toMonth "
            + "AND t.status = com.mecfin.transaction.domain.TransactionStatus.POSTED AND t.type = :type "
            + "GROUP BY t.categoryId, t.competenceMonth")
    List<CategoryMonthTotalProjection> sumByCategoryAndMonth(
            @Param("accountIds") List<UUID> accountIds,
            @Param("fromMonth") LocalDate fromMonth,
            @Param("toMonth") LocalDate toMonth,
            @Param("type") TransactionType type);
}
