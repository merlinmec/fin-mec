package com.mecfin.transaction.application;

import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.domain.RecurringSeries;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.infra.RecurringSeriesRepository;
import com.mecfin.transaction.infra.TransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Motor de recorrência (Fase 11). Materializa as ocorrências de um lançamento fixo como
 * {@link Transaction}s comuns numa janela deslizante de {@value #HORIZON_MONTHS} meses à frente:
 * <ul>
 *   <li>ocorrência com data até hoje nasce com o status pedido pelo usuário (quem cadastra um
 *       fixo que começou há dois meses está registrando o que já aconteceu);</li>
 *   <li>ocorrência futura nasce PENDING - não entra no saldo nem no gasto do orçamento até ser
 *       efetivada, mas aparece na listagem do mês e na previsão do dashboard.</li>
 * </ul>
 * O job diário ({@link #extendAllHorizons()}) só empurra a janela para frente; nunca recria uma
 * ocorrência já emitida (ver {@link RecurringSeries#claimNextIndex()}).
 */
@Service
public class RecurringSeriesService {

    static final int HORIZON_MONTHS = 12;
    // Trava de segurança contra laço longo (série semanal com data inicial muito antiga).
    private static final int MAX_OCCURRENCES_PER_RUN = 600;

    private static final Logger log = LoggerFactory.getLogger(RecurringSeriesService.class);

    private final RecurringSeriesRepository seriesRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public RecurringSeriesService(
            RecurringSeriesRepository seriesRepository,
            TransactionRepository transactionRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.seriesRepository = seriesRepository;
        this.transactionRepository = transactionRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Cria a série e materializa a ocorrência 0 (com a competência pedida) e as seguintes até o
     * horizonte. Devolve a ocorrência 0, que é o "lançamento" que o usuário acabou de criar.
     */
    @Transactional
    public Transaction start(RecurringSeries series, TransactionStatus firstStatus, YearMonth firstCompetence) {
        RecurringSeries saved = seriesRepository.save(series);
        int index = saved.claimNextIndex();
        Transaction first = transactionRepository.save(Transaction.recurrenceOccurrence(
                saved, index, saved.occurrenceDate(index), firstCompetence, firstStatus));
        generateUntilHorizon(saved, firstStatus);
        return first;
    }

    /** Transforma um lançamento avulso já existente na ocorrência 0 de uma série nova. */
    @Transactional
    public void startFrom(Transaction anchor, RecurringSeries series) {
        RecurringSeries saved = seriesRepository.save(series);
        anchor.attachToSeries(saved, saved.claimNextIndex());
        generateUntilHorizon(saved, anchor.getStatus());
    }

    public List<RecurringSeriesView> list() {
        return seriesRepository.findAllByHouseholdIdOrderByCreatedAtDesc(CurrentUser.householdId()).stream()
                .map(this::toView)
                .toList();
    }

    /** Encerra a série a partir de hoje: cancela as ocorrências futuras ainda pendentes. */
    @Transactional
    public void stop(UUID seriesId) {
        RecurringSeries series = getOwnedOrThrow(seriesId);
        LocalDate today = LocalDate.now(clock);
        for (Transaction occurrence : transactionRepository
                .findAllByRecurrenceSeriesIdAndStatusOrderByRecurrenceIndexAsc(series.getId(), TransactionStatus.PENDING)) {
            if (!occurrence.getTransactionDate().isBefore(today)) {
                occurrence.cancel();
            }
        }
        series.endBefore(today);
    }

    /** Edição "esta e as próximas": muda o molde e todas as ocorrências pendentes seguintes. */
    @Transactional
    public void applyTemplateFrom(Transaction from) {
        RecurringSeries series = seriesRepository.findById(from.getRecurrenceSeriesId())
                .orElseThrow(() -> new IllegalStateException("Série não encontrada: " + from.getRecurrenceSeriesId()));
        series.updateTemplate(from.getCategoryId(), from.getType(), from.getAmount(), from.getDescription());
        for (Transaction occurrence : laterPendingOccurrences(from)) {
            occurrence.applyTemplate(from.getCategoryId(), from.getType(), from.getAmount(), from.getDescription());
        }
    }

    /** Exclusão "esta e as próximas": cancela a ocorrência, as pendentes seguintes e encerra a série. */
    @Transactional
    public void cancelFrom(Transaction from) {
        RecurringSeries series = seriesRepository.findById(from.getRecurrenceSeriesId())
                .orElseThrow(() -> new IllegalStateException("Série não encontrada: " + from.getRecurrenceSeriesId()));
        from.cancel();
        laterPendingOccurrences(from).forEach(Transaction::cancel);
        series.endBefore(series.occurrenceDate(from.getRecurrenceIndex()));
    }

    /**
     * Job diário: estende a janela de todas as séries ativas. Cada série roda na sua própria
     * transação - uma série com problema (ex.: conta excluída) não impede as outras.
     */
    @Scheduled(cron = "${mecfin.recurrence.cron:0 10 3 * * *}")
    public void extendAllHorizons() {
        int extended = 0;
        for (RecurringSeries candidate : seriesRepository.findAllByActiveTrue()) {
            try {
                Integer generated = requiresNew.execute(status -> seriesRepository.findById(candidate.getId())
                        .map(series -> generateUntilHorizon(series, TransactionStatus.PENDING))
                        .orElse(0));
                extended += generated != null ? generated : 0;
            } catch (RuntimeException e) {
                log.warn("Falha ao estender a série de recorrência {}", candidate.getId(), e);
            }
        }
        log.info("Motor de recorrência: {} ocorrência(s) gerada(s)", extended);
    }

    private int generateUntilHorizon(RecurringSeries series, TransactionStatus pastStatus) {
        LocalDate today = LocalDate.now(clock);
        LocalDate horizon = today.plusMonths(HORIZON_MONTHS);
        List<Transaction> occurrences = new ArrayList<>();
        while (series.hasOccurrenceUntil(horizon) && occurrences.size() < MAX_OCCURRENCES_PER_RUN) {
            int index = series.claimNextIndex();
            LocalDate date = series.occurrenceDate(index);
            TransactionStatus status = date.isAfter(today) ? TransactionStatus.PENDING : pastStatus;
            occurrences.add(Transaction.recurrenceOccurrence(series, index, date, YearMonth.from(date), status));
        }
        transactionRepository.saveAll(occurrences);
        return occurrences.size();
    }

    private List<Transaction> laterPendingOccurrences(Transaction from) {
        return transactionRepository
                .findAllByRecurrenceSeriesIdAndStatusOrderByRecurrenceIndexAsc(
                        from.getRecurrenceSeriesId(), TransactionStatus.PENDING)
                .stream()
                .filter(occurrence -> occurrence.getRecurrenceIndex() > from.getRecurrenceIndex())
                .toList();
    }

    private RecurringSeriesView toView(RecurringSeries series) {
        LocalDate nextPending = transactionRepository.findNextPendingDate(series.getId());
        return new RecurringSeriesView(series, nextPending);
    }

    private RecurringSeries getOwnedOrThrow(UUID id) {
        return seriesRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new RecurringSeriesNotFoundException(id));
    }
}
