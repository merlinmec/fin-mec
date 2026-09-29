package com.mecfin.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.transaction.domain.RecurringSeries;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.RecurringSeriesRepository;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class RecurringSeriesServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Mock
    private RecurringSeriesRepository seriesRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private final List<Transaction> saved = new ArrayList<>();
    private RecurringSeriesService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new RecurringSeriesService(
                seriesRepository, transactionRepository, mock(PlatformTransactionManager.class), clock);
    }

    private void captureSaves() {
        when(seriesRepository.save(any(RecurringSeries.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(transactionRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<Transaction> batch = invocation.getArgument(0);
            batch.forEach(saved::add);
            return batch;
        });
    }

    private RecurringSeries monthly(LocalDate start, LocalDate end) {
        return new RecurringSeries(UUID.randomUUID(), UUID.randomUUID(), null, TransactionType.EXPENSE,
                new BigDecimal("1500.00"), "Aluguel", RecurrenceRule.MONTHLY, start, end);
    }

    @Test
    void startGeneratesTwelveMonthsAheadWithFutureOccurrencesPending() {
        captureSaves();

        Transaction first = service.start(monthly(TODAY, null), TransactionStatus.POSTED, YearMonth.from(TODAY));

        // Hoje + 12 meses, inclusive: ocorrências 0..12.
        assertThat(saved).hasSize(13);
        assertThat(first.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(first.getRecurrenceIndex()).isZero();
        assertThat(saved.subList(1, saved.size()))
                .allSatisfy(occurrence -> assertThat(occurrence.getStatus()).isEqualTo(TransactionStatus.PENDING));
        assertThat(saved.get(12).getTransactionDate()).isEqualTo(TODAY.plusMonths(12));
        assertThat(saved.get(12).getCompetenceMonth()).isEqualTo(YearMonth.from(TODAY.plusMonths(12)));
    }

    @Test
    void pastOccurrencesOfABackfilledSeriesKeepTheRequestedStatus() {
        captureSaves();

        service.start(monthly(TODAY.minusMonths(2), null), TransactionStatus.POSTED, YearMonth.from(TODAY.minusMonths(2)));

        assertThat(saved.get(0).getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(saved.get(1).getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(saved.get(2).getStatus()).isEqualTo(TransactionStatus.POSTED); // hoje
        assertThat(saved.get(3).getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void endDateLimitsGeneration() {
        captureSaves();

        service.start(monthly(TODAY, TODAY.plusMonths(2)), TransactionStatus.POSTED, YearMonth.from(TODAY));

        assertThat(saved).hasSize(3);
    }

    @Test
    void cancelFromCancelsLaterPendingOccurrencesAndEndsTheSeries() {
        captureSaves();
        RecurringSeries series = monthly(TODAY, null);
        service.start(series, TransactionStatus.POSTED, YearMonth.from(TODAY));
        Transaction third = saved.get(3);
        List<Transaction> pending = saved.stream()
                .filter(occurrence -> occurrence.getStatus() == TransactionStatus.PENDING)
                .toList();
        when(seriesRepository.findById(third.getRecurrenceSeriesId())).thenReturn(Optional.of(series));
        when(transactionRepository.findAllByRecurrenceSeriesIdAndStatusOrderByRecurrenceIndexAsc(
                third.getRecurrenceSeriesId(), TransactionStatus.PENDING)).thenReturn(pending);

        service.cancelFrom(third);

        assertThat(saved.subList(0, 3)).noneSatisfy(
                occurrence -> assertThat(occurrence.getStatus()).isEqualTo(TransactionStatus.CANCELED));
        assertThat(saved.subList(3, saved.size())).allSatisfy(
                occurrence -> assertThat(occurrence.getStatus()).isEqualTo(TransactionStatus.CANCELED));
        assertThat(series.isActive()).isFalse();
        assertThat(series.getEndDate()).isEqualTo(TODAY.plusMonths(3).minusDays(1));
    }

    @Test
    void generationNeverReissuesAnIndex() {
        captureSaves();
        RecurringSeries series = monthly(TODAY, null);
        service.start(series, TransactionStatus.POSTED, YearMonth.from(TODAY));
        int nextIndexAfterStart = series.getNextIndex();

        // Nada novo cabe na janela no mesmo dia: o gerador não volta a índices já emitidos.
        assertThat(series.hasOccurrenceUntil(TODAY.plusMonths(12))).isFalse();
        assertThat(nextIndexAfterStart).isEqualTo(13);
    }
}
