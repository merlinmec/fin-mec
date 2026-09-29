package com.mecfin.importing.application;

import com.mecfin.importing.domain.DescriptionNormalizer;
import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.importing.domain.CategorizationRule;
import com.mecfin.importing.domain.ImportBatch;
import com.mecfin.importing.domain.ImportFormat;
import com.mecfin.importing.infra.CategorizationRuleRepository;
import com.mecfin.importing.infra.ImportBatchRepository;
import com.mecfin.shared.exception.ConflictException;
import com.mecfin.shared.exception.NotFoundException;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação de extrato em duas etapas (Fase 15):
 * <ol>
 *   <li>{@link #preview}: lê o arquivo e diz o que aconteceria com cada linha — nada é gravado;</li>
 *   <li>{@link #commit}: grava exatamente o que o usuário confirmou na tela.</li>
 * </ol>
 * O arquivo nunca é guardado: a pré-visualização é stateless e o commit recebe as linhas de
 * volta. Tudo é revalidado no commit (conta, categoria, tags, previsto) como num lançamento
 * manual, e a idempotência final é garantida pelo índice único (conta, external_id).
 */
@Service
public class ImportService {

    public static final int MAX_ROWS = 2000;
    // Janela para casar linha do extrato com um previsto: fixo cai em dia útil diferente, e conta
    // de consumo (luz, água) varia de valor mês a mês.
    private static final int MATCH_DAYS = 7;
    private static final BigDecimal MATCH_TOLERANCE = new BigDecimal("0.15");
    private static final BigDecimal MATCH_MIN_TOLERANCE = BigDecimal.ONE;
    private static final int HISTORY_SIZE = 1000;

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final CategorizationRuleRepository ruleRepository;
    private final ImportBatchRepository batchRepository;

    public ImportService(AccountService accountService, TransactionRepository transactionRepository,
            TransactionService transactionService, CategorizationRuleRepository ruleRepository,
            ImportBatchRepository batchRepository) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.ruleRepository = ruleRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public ImportPreview preview(UUID accountId, String fileName, byte[] content, CsvMapping csvMapping) {
        requireAccount(accountId);
        String text = StatementText.decode(content);
        ImportFormat format = OfxParser.looksLikeOfx(text) ? ImportFormat.OFX : ImportFormat.CSV;
        List<StatementLine> lines;
        List<String> columns = null;
        CsvMapping mapping = null;
        if (format == ImportFormat.OFX) {
            lines = OfxParser.parse(text);
        } else {
            CsvStatementParser.Parsed parsed = CsvStatementParser.parse(text, csvMapping);
            lines = parsed.lines();
            columns = parsed.columns();
            mapping = parsed.mapping();
        }
        if (lines.size() > MAX_ROWS) {
            throw new StatementFormatException("O arquivo tem " + lines.size() + " lançamentos; o limite é " + MAX_ROWS
                    + " por importação — exporte um período menor");
        }
        List<PreviewRow> rows = analyze(accountId, lines);
        return new ImportPreview(format, fileName, columns, mapping, rows,
                count(rows, PreviewStatus.NEW), count(rows, PreviewStatus.ALREADY_IMPORTED),
                count(rows, PreviewStatus.POSSIBLE_DUPLICATE), count(rows, PreviewStatus.MATCHES_PENDING));
    }

    @Transactional
    public ImportResult commit(UUID accountId, String fileName, ImportFormat format, List<CommitRow> rows) {
        requireAccount(accountId);
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Máximo de " + MAX_ROWS + " linhas por importação");
        }
        ImportBatch batch = batchRepository.save(new ImportBatch(CurrentUser.householdId(), accountId,
                OfxParser.truncate(fileName == null || fileName.isBlank() ? "extrato" : fileName, 255), format));

        Set<String> alreadyUsed = new HashSet<>(transactionRepository.findExternalIds(accountId,
                rows.stream().map(CommitRow::externalId).filter(id -> id != null).toList()));
        Set<UUID> matchedPending = new HashSet<>();
        int created = 0;
        int matched = 0;
        int skipped = 0;
        for (CommitRow row : rows) {
            String externalId = row.externalId();
            boolean duplicate = externalId == null || !alreadyUsed.add(externalId);
            if (row.action() == CommitRow.Action.SKIP || duplicate || row.amount() == null || row.amount().signum() == 0) {
                skipped++;
                continue;
            }
            if (row.action() == CommitRow.Action.MATCH && row.matchTransactionId() != null
                    && matchedPending.add(row.matchTransactionId())
                    && transactionService.confirmFromStatement(row.matchTransactionId(), accountId, row.amount().abs(),
                            row.date(), externalId)) {
                matched++;
                continue;
            }
            transactionService.createImported(accountId, row.categoryId(), typeOf(row.amount()), row.amount().abs(),
                    row.description(), row.date(), externalId, batch.getId(), row.tagIds());
            created++;
        }
        batch.record(created, matched, skipped);
        return new ImportResult(batch.getId(), created, matched, skipped);
    }

    /**
     * Importação sem revisão humana (sincronização bancária, Fase 16): aplica a própria análise da
     * pré-visualização — cria o que é novo, efetiva o previsto que casar e ignora o resto. O
     * "possível duplicado" de um lançamento manual é ignorado: na dúvida, nunca dobrar um gasto
     * (o usuário ainda pode trazê-lo pela importação manual). Sem nada a gravar, nem cria lote.
     */
    @Transactional
    public ImportResult autoImport(UUID accountId, String label, ImportFormat format, List<StatementLine> lines) {
        if (lines.isEmpty()) {
            return new ImportResult(null, 0, 0, 0);
        }
        List<PreviewRow> rows = analyze(accountId, lines);
        boolean anythingToWrite = rows.stream().anyMatch(r -> r.status() == PreviewStatus.NEW
                || r.status() == PreviewStatus.MATCHES_PENDING);
        if (!anythingToWrite) {
            return new ImportResult(null, 0, 0, rows.size());
        }
        List<CommitRow> commitRows = rows.stream().map(row -> new CommitRow(
                row.externalId(), row.date(), row.description(),
                row.type() == TransactionType.EXPENSE ? row.amount().negate() : row.amount(),
                switch (row.status()) {
                    case NEW -> CommitRow.Action.CREATE;
                    case MATCHES_PENDING -> CommitRow.Action.MATCH;
                    default -> CommitRow.Action.SKIP;
                },
                row.suggestedCategoryId(),
                row.suggestedTagId() != null ? List.of(row.suggestedTagId()) : null,
                row.matchTransactionId())).toList();
        return commit(accountId, label, format, commitRows);
    }

    public List<ImportBatch> history() {
        return batchRepository.findTop30ByHouseholdIdOrderByCreatedAtDesc(CurrentUser.householdId());
    }

    /**
     * Desfaz uma importação: cancela (estorno, nunca apaga) os lançamentos que ela criou e solta o
     * vínculo com o extrato, para que o arquivo possa ser reimportado depois. Previstos que ela
     * efetivou continuam efetivados — eram lançamentos que já existiam antes dela.
     */
    @Transactional
    public int undo(UUID batchId) {
        ImportBatch batch = batchRepository.findByIdAndHouseholdId(batchId, CurrentUser.householdId())
                .orElseThrow(() -> new NotFoundException("Importação não encontrada: " + batchId));
        if (batch.isUndone()) {
            throw new ConflictException("Esta importação já foi desfeita");
        }
        List<Transaction> created = transactionRepository.findAllByImportBatchId(batch.getId());
        created.forEach(Transaction::undoImport);
        batch.markUndone();
        return created.size();
    }

    private List<PreviewRow> analyze(UUID accountId, List<StatementLine> lines) {
        UUID householdId = CurrentUser.householdId();
        Set<String> imported = new HashSet<>(transactionRepository.findExternalIds(accountId,
                lines.stream().map(StatementLine::externalId).toList()));

        LocalDate from = lines.stream().map(StatementLine::date).min(Comparator.naturalOrder()).orElseThrow()
                .minusDays(MATCH_DAYS);
        LocalDate to = lines.stream().map(StatementLine::date).max(Comparator.naturalOrder()).orElseThrow()
                .plusDays(MATCH_DAYS);
        List<Transaction> existing = transactionRepository.findForStatementMatching(accountId, from, to);
        List<Transaction> manual = new ArrayList<>(existing.stream()
                .filter(t -> t.getStatus() == TransactionStatus.POSTED && t.getExternalId() == null)
                .toList());
        List<Transaction> pending = new ArrayList<>(existing.stream()
                .filter(t -> t.getStatus() == TransactionStatus.PENDING)
                .toList());

        List<CategorizationRule> rules = ruleRepository.findAllByHouseholdIdOrderByPatternAsc(householdId);
        Map<String, UUID> history = categoryHistory();

        List<PreviewRow> rows = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        for (StatementLine line : lines) {
            TransactionType type = typeOf(line.amount());
            BigDecimal amount = line.amount().abs();
            String normalized = DescriptionNormalizer.normalize(line.description());

            UUID categoryId = null;
            UUID tagId = null;
            String source = null;
            Optional<CategorizationRule> rule = CategorizationRuleService.bestMatch(rules, normalized);
            if (rule.isPresent()) {
                categoryId = rule.get().getCategoryId();
                tagId = rule.get().getTagId();
                source = "RULE";
            } else {
                UUID learned = learnedCategory(history, normalized);
                if (learned != null) {
                    categoryId = learned;
                    source = "HISTORY";
                }
            }

            if (imported.contains(line.externalId()) || !seenInFile.add(line.externalId())) {
                rows.add(row(line, amount, type, PreviewStatus.ALREADY_IMPORTED, categoryId, tagId, source, null));
                continue;
            }
            Transaction pendingMatch = takePendingMatch(pending, type, amount, line.date());
            if (pendingMatch != null) {
                // O previsto já tem categoria (a do fixo); só sugere a da regra se ele não tiver.
                UUID category = pendingMatch.getCategoryId() != null ? pendingMatch.getCategoryId() : categoryId;
                rows.add(row(line, amount, type, PreviewStatus.MATCHES_PENDING, category, tagId, source, pendingMatch));
                continue;
            }
            Transaction manualMatch = takeManualDuplicate(manual, type, amount, line.date());
            PreviewStatus status = manualMatch != null ? PreviewStatus.POSSIBLE_DUPLICATE : PreviewStatus.NEW;
            rows.add(row(line, amount, type, status, categoryId, tagId, source, manualMatch));
        }
        return rows;
    }

    private Transaction takePendingMatch(List<Transaction> pending, TransactionType type, BigDecimal amount,
            LocalDate date) {
        BigDecimal tolerance = amount.multiply(MATCH_TOLERANCE).max(MATCH_MIN_TOLERANCE);
        Optional<Transaction> best = pending.stream()
                .filter(t -> t.getType() == type)
                .filter(t -> t.getAmount().subtract(amount).abs().compareTo(tolerance) <= 0)
                .filter(t -> Math.abs(t.getTransactionDate().toEpochDay() - date.toEpochDay()) <= MATCH_DAYS)
                .min(Comparator.comparingLong(t -> Math.abs(t.getTransactionDate().toEpochDay() - date.toEpochDay())));
        best.ifPresent(pending::remove);
        return best.orElse(null);
    }

    private Transaction takeManualDuplicate(List<Transaction> manual, TransactionType type, BigDecimal amount,
            LocalDate date) {
        Optional<Transaction> match = manual.stream()
                .filter(t -> t.getType() == type && t.getAmount().compareTo(amount) == 0 && t.getTransactionDate().equals(date))
                .findFirst();
        match.ifPresent(manual::remove);
        return match.orElse(null);
    }

    // Descrição normalizada -> categoria do lançamento mais recente com essa descrição. É o
    // "aprendizado" sem regra explícita: categorizou "Padaria X" uma vez, a próxima já vem certa.
    private Map<String, UUID> categoryHistory() {
        Map<String, UUID> history = new HashMap<>();
        for (Transaction t : transactionRepository.findRecentCategorized(accountService.householdAccountIds(),
                org.springframework.data.domain.PageRequest.of(0, HISTORY_SIZE))) {
            history.putIfAbsent(DescriptionNormalizer.normalize(t.getDescription()), t.getCategoryId());
        }
        return history;
    }

    /**
     * Categoria aprendida do histórico: descrição idêntica (normalizada) primeiro; senão, a
     * descrição já categorizada mais longa que aparece como palavras inteiras dentro da linha do
     * banco — quem lançou "Mercado" à mão ensina "COMPRA CARTAO 4432 MERCADO EXTRA". Mínimo de 4
     * letras para "bar"/"pix" não casarem com tudo.
     */
    static UUID learnedCategory(Map<String, UUID> history, String normalized) {
        UUID exact = history.get(normalized);
        if (exact != null) {
            return exact;
        }
        String padded = " " + normalized + " ";
        String best = null;
        for (String known : history.keySet()) {
            if (known.length() >= 4 && padded.contains(" " + known + " ")
                    && (best == null || known.length() > best.length())) {
                best = known;
            }
        }
        return best != null ? history.get(best) : null;
    }

    private static PreviewRow row(StatementLine line, BigDecimal amount, TransactionType type, PreviewStatus status,
            UUID categoryId, UUID tagId, String source, Transaction match) {
        return new PreviewRow(line.externalId(), line.date(), line.description(), amount, type, status, categoryId,
                tagId, source,
                match != null ? match.getId() : null,
                match != null ? match.getDescription() : null,
                match != null ? match.getTransactionDate() : null,
                match != null ? match.getAmount() : null);
    }

    private static TransactionType typeOf(BigDecimal signedAmount) {
        return signedAmount.signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
    }

    private static long count(List<PreviewRow> rows, PreviewStatus status) {
        return rows.stream().filter(r -> r.status() == status).count();
    }

    private void requireAccount(UUID accountId) {
        try {
            accountService.get(accountId);
        } catch (AccountNotFoundException e) {
            throw new IllegalArgumentException("accountId inválido ou não visível: " + accountId);
        }
    }
}
