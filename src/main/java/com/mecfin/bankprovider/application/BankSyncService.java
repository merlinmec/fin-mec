package com.mecfin.bankprovider.application;

import com.mecfin.bankprovider.domain.BankAccountLink;
import com.mecfin.bankprovider.domain.BankAccountLinkMode;
import com.mecfin.bankprovider.domain.BankConnection;
import com.mecfin.bankprovider.domain.BankConnectionStatus;
import com.mecfin.bankprovider.infra.BankAccountLinkRepository;
import com.mecfin.bankprovider.infra.BankConnectionRepository;
import com.mecfin.importing.application.ImportResult;
import com.mecfin.importing.application.ImportService;
import com.mecfin.importing.application.StatementLine;
import com.mecfin.importing.domain.ImportFormat;
import com.mecfin.shared.security.SystemContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sincronização com o banco (Fase 16). Três gatilhos: botão "Sincronizar agora", webhook do
 * provedor e um job diário (rede de segurança para webhook perdido). Os lançamentos passam pelo
 * MESMO pipeline da importação de extrato (Fase 15): external_id "pluggy:{id}" + índice único
 * garantem que nada duplica; linha que casa com previsto efetiva o fixo; regras e histórico
 * sugerem a categoria; cada sincronização vira um lote com "desfazer".
 */
@Service
public class BankSyncService {

    private static final Logger log = LoggerFactory.getLogger(BankSyncService.class);
    // Relê alguns dias antes da última sincronização: o banco às vezes publica um lançamento com
    // data retroativa. A deduplicação torna a sobreposição inofensiva.
    private static final int OVERLAP_DAYS = 10;

    private final BankProviderClient provider;
    private final BankConnectionRepository connections;
    private final BankAccountLinkRepository links;
    private final BankConnectionService connectionService;
    private final ImportService importService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BankSyncService(BankProviderClient provider, BankConnectionRepository connections,
            BankAccountLinkRepository links, BankConnectionService connectionService, ImportService importService,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.provider = provider;
        this.connections = connections;
        this.links = links;
        this.connectionService = connectionService;
        this.importService = importService;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** "Sincronizar agora" — a conexão precisa ser do household do usuário logado. */
    public SyncResult syncOwned(UUID connectionId) {
        return sync(connectionService.getOwned(connectionId).getId());
    }

    /** Webhook: o corpo não é confiável (a Pluggy não assina) — só diz QUAL item reler. */
    @Async
    public void syncByItemAsync(String itemId) {
        connections.findByProviderAndExternalItemId(provider.providerName(), itemId)
                .filter(c -> c.getStatus() != BankConnectionStatus.DISCONNECTED)
                .ifPresent(c -> {
                    try {
                        sync(c.getId());
                    } catch (RuntimeException e) {
                        log.warn("Sincronização via webhook falhou para a conexão {}", c.getId(), e);
                    }
                });
    }

    @Scheduled(cron = "${mecfin.pluggy.sync-cron:0 30 6 * * *}")
    @SchedulerLock(name = "bank-sync-all", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void syncAll() {
        if (!provider.isConfigured()) {
            return;
        }
        for (BankConnection connection : connections.findAll()) {
            if (connection.getStatus() == BankConnectionStatus.ACTIVE || connection.getStatus() == BankConnectionStatus.ERROR) {
                try {
                    sync(connection.getId());
                } catch (RuntimeException e) {
                    log.warn("Sincronização diária falhou para a conexão {}", connection.getId(), e);
                }
            }
        }
    }

    /**
     * Uma sincronização completa. Sem transação única de propósito: chamadas HTTP ao provedor não
     * seguram conexão de banco aberta; cada etapa grava na sua própria transação curta.
     */
    SyncResult sync(UUID connectionId) {
        BankConnection snapshot = connections.findById(connectionId).orElseThrow();
        return SystemContext.runAsHousehold(snapshot.getHouseholdId(), () -> {
            ExternalItem item;
            try {
                item = provider.getItem(snapshot.getExternalItemId());
            } catch (RuntimeException e) {
                String reason = "Não foi possível falar com o banco agora; vamos tentar de novo";
                tx.executeWithoutResult(s -> connections.findById(connectionId).ifPresent(c -> c.markError(reason)));
                log.warn("Falha ao consultar o item {}", snapshot.getExternalItemId(), e);
                return new SyncResult(BankConnectionStatus.ERROR, 0, 0, 0, reason);
            }
            if (item.health() == ExternalItem.Health.NEEDS_RECONNECT) {
                tx.executeWithoutResult(s -> connections.findById(connectionId).ifPresent(c -> c.markExpired(item.error())));
                return new SyncResult(BankConnectionStatus.EXPIRED, 0, 0, 0, item.error());
            }
            tx.executeWithoutResult(s -> connectionService.refreshAccounts(connections.findById(connectionId).orElseThrow()));

            String label = "Sincronização · " + connectionService.institutionName(snapshot);
            LocalDate from = snapshot.getLastSyncedAt() == null
                    ? snapshot.getSyncFrom()
                    : LocalDate.ofInstant(snapshot.getLastSyncedAt(), clock.getZone()).minusDays(OVERLAP_DAYS);
            if (from == null || (snapshot.getSyncFrom() != null && from.isBefore(snapshot.getSyncFrom()))) {
                from = snapshot.getSyncFrom() != null ? snapshot.getSyncFrom() : LocalDate.now(clock).minusDays(90);
            }
            int created = 0;
            int matched = 0;
            int skipped = 0;
            for (BankAccountLink link : links.findAllByBankConnectionIdOrderByNameAsc(connectionId)) {
                if (link.getMode() != BankAccountLinkMode.LINKED || link.getAccountId() == null) {
                    continue;
                }
                List<StatementLine> lines = provider.getTransactions(link.getExternalAccountId(), from).stream()
                        .filter(ExternalBankTransaction::posted)
                        .map(t -> new StatementLine("pluggy:" + t.externalTransactionId(), t.date(), t.amount(),
                                t.description().length() <= 255 ? t.description() : t.description().substring(0, 255)))
                        .toList();
                ImportResult result = importService.autoImport(link.getAccountId(), label, ImportFormat.BANK_SYNC, lines);
                created += result.created();
                matched += result.matched();
                skipped += result.skipped();
            }
            String warning = item.health() == ExternalItem.Health.ERROR ? item.error() : null;
            tx.executeWithoutResult(s -> connections.findById(connectionId).ifPresent(c -> {
                if (warning != null) {
                    c.markError(warning);
                } else {
                    c.markSynced(clock.instant());
                }
            }));
            return new SyncResult(warning != null ? BankConnectionStatus.ERROR : BankConnectionStatus.ACTIVE,
                    created, matched, skipped, warning);
        });
    }
}
