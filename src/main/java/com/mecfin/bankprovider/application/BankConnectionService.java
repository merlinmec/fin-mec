package com.mecfin.bankprovider.application;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bankprovider.domain.BankAccountLink;
import com.mecfin.bankprovider.domain.BankConnection;
import com.mecfin.bankprovider.domain.BankConnectionStatus;
import com.mecfin.bankprovider.domain.BankInstitution;
import com.mecfin.bankprovider.infra.BankAccountLinkRepository;
import com.mecfin.bankprovider.infra.BankConnectionRepository;
import com.mecfin.bankprovider.infra.BankInstitutionRepository;
import com.mecfin.household.domain.HouseholdErasingEvent;
import com.mecfin.shared.exception.ConflictException;
import com.mecfin.shared.exception.NotFoundException;
import com.mecfin.shared.exception.UpstreamUnavailableException;
import com.mecfin.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conexões bancárias do household (Fase 16).
 *
 * Segurança: todos os households do fin-mec compartilham UMA conta no provedor. O connect token
 * carimba o item com o household (clientUserId), e registrar um item confere esse carimbo — sem
 * isso, qualquer usuário que descobrisse o id do item de outra pessoa poderia plugá-lo na própria
 * conta e ler o extrato dela.
 */
@Service
public class BankConnectionService {

    private static final Logger log = LoggerFactory.getLogger(BankConnectionService.class);

    public record Status(boolean enabled, boolean includeSandbox, String provider) {
    }

    public enum SetupMode {
        CREATE,
        LINK,
        IGNORE
    }

    private final BankProviderClient provider;
    private final BankConnectionRepository connections;
    private final BankInstitutionRepository institutions;
    private final BankAccountLinkRepository links;
    private final AccountService accountService;
    private final Clock clock;
    private final String baseUrl;
    private final String webhookSecret;
    private final boolean includeSandbox;
    private final int historyDays;

    public BankConnectionService(BankProviderClient provider, BankConnectionRepository connections,
            BankInstitutionRepository institutions, BankAccountLinkRepository links, AccountService accountService,
            Clock clock, @Value("${mecfin.app.base-url}") String baseUrl,
            @Value("${mecfin.pluggy.webhook-secret:}") String webhookSecret,
            @Value("${mecfin.pluggy.include-sandbox:false}") boolean includeSandbox,
            @Value("${mecfin.pluggy.history-days:90}") int historyDays) {
        this.provider = provider;
        this.connections = connections;
        this.institutions = institutions;
        this.links = links;
        this.accountService = accountService;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.webhookSecret = webhookSecret;
        this.includeSandbox = includeSandbox;
        this.historyDays = historyDays;
    }

    public Status status() {
        return new Status(provider.isConfigured(), includeSandbox, provider.providerName());
    }

    /** Token para o widget. connectionId != null = reconectar uma conexão existente. */
    /** itemId só vem preenchido na reconexão: o widget precisa dele em {@code updateItem}. */
    public record ConnectToken(String accessToken, String itemId) {
    }

    public ConnectToken connectToken(UUID connectionId) {
        requireEnabled();
        String existingItem = connectionId == null ? null : getOwned(connectionId).getExternalItemId();
        String token = call(() -> provider.createConnectToken(CurrentUser.householdId().toString(), existingItem,
                webhookUrl()));
        return new ConnectToken(token, existingItem);
    }

    @Transactional
    public BankConnectionView register(String itemId) {
        requireEnabled();
        UUID householdId = CurrentUser.householdId();
        ExternalItem item = call(() -> provider.getItem(itemId));
        if (!householdId.toString().equals(item.clientUserId())) {
            throw new IllegalArgumentException("Esta conexão bancária não pertence à sua conta");
        }
        BankInstitution institution = institutions.findByProviderCode(item.institutionCode())
                .orElseGet(() -> institutions.save(new BankInstitution(
                        item.institutionName() == null ? "Banco" : item.institutionName(), item.institutionCode())));
        institution.updateBranding(item.institutionName(), item.institutionImageUrl(), item.institutionColor());

        BankConnection connection = connections.findByProviderAndExternalItemId(provider.providerName(), itemId)
                .map(existing -> {
                    if (!existing.getHouseholdId().equals(householdId)) {
                        throw new IllegalArgumentException("Esta conexão bancária não pertence à sua conta");
                    }
                    existing.reactivate();
                    return existing;
                })
                .orElseGet(() -> {
                    BankConnection created = new BankConnection(householdId, institution.getId(),
                            provider.providerName(), itemId, null);
                    created.startHistoryFrom(LocalDate.now(clock).minusDays(historyDays));
                    return connections.save(created);
                });
        call(() -> {
            refreshAccounts(connection);
            return null;
        });
        return view(connection);
    }

    public List<BankConnectionView> list() {
        return connections.findAllByHouseholdId(CurrentUser.householdId()).stream()
                .filter(c -> c.getStatus() != BankConnectionStatus.DISCONNECTED)
                .sorted(Comparator.comparing(BankConnection::getCreatedAt))
                .map(this::view)
                .toList();
    }

    /**
     * Decide para onde vão os lançamentos de uma conta do banco:
     * CREATE cria uma conta no fin-mec com saldo inicial calculado para que, depois de importado o
     * histórico, o saldo do fin-mec bata com o do banco; LINK usa uma conta que já existe; IGNORE
     * não sincroniza.
     */
    @Transactional
    public BankConnectionView setupAccount(UUID connectionId, UUID linkId, SetupMode mode, UUID accountId) {
        BankConnection connection = getOwned(connectionId);
        BankAccountLink link = links.findByIdAndBankConnectionIdAndHouseholdId(linkId, connectionId,
                        CurrentUser.householdId())
                .orElseThrow(() -> new NotFoundException("Conta do banco não encontrada: " + linkId));
        switch (mode) {
            case IGNORE -> link.ignore();
            case LINK -> {
                if (accountId == null) {
                    throw new IllegalArgumentException("Escolha a conta do fin-mec");
                }
                try {
                    accountService.get(accountId);
                } catch (AccountNotFoundException e) {
                    throw new IllegalArgumentException("accountId inválido ou não visível: " + accountId);
                }
                if (links.existsByAccountIdAndIdNot(accountId, link.getId())) {
                    throw new ConflictException("Esta conta do fin-mec já recebe lançamentos de outro banco");
                }
                link.linkTo(accountId);
            }
            case CREATE -> {
                BigDecimal initial = call(() -> initialBalanceFor(connection, link));
                Account account = accountService.create(institutionName(connection) + " · " + link.getName(),
                        AccountType.CHECKING, initial);
                link.linkTo(account.getId());
            }
        }
        return view(connection);
    }

    @Transactional
    public void disconnect(UUID connectionId) {
        BankConnection connection = getOwned(connectionId);
        try {
            provider.deleteItem(connection.getExternalItemId());
        } catch (RuntimeException e) {
            // Mesmo com o provedor fora, a conexão sai do app; o item órfão expira no provedor.
            log.warn("Falha ao remover o item {} no provedor", connection.getExternalItemId(), e);
        }
        connection.disconnect();
    }

    /**
     * O household vai ser apagado (exclusão da conta do último membro, ou troca do household
     * pessoal por um compartilhado): remove as conexões também no provedor — senão o acesso ao
     * banco continuaria autorizado lá fora. Antes da Fase 17 isto reagia à exclusão de QUALQUER
     * usuário e, num household compartilhado, desconectaria o banco de todo mundo.
     */
    @EventListener
    public void onHouseholdErasing(HouseholdErasingEvent event) {
        for (BankConnection connection : connections.findAllByHouseholdId(event.householdId())) {
            if (connection.getStatus() != BankConnectionStatus.DISCONNECTED) {
                try {
                    provider.deleteItem(connection.getExternalItemId());
                } catch (RuntimeException e) {
                    log.warn("Falha ao remover o item {} no provedor ao apagar o household",
                            connection.getExternalItemId(), e);
                }
            }
        }
    }

    /** Atualiza a lista de contas do banco (nome, número, saldo) — novas entram como PENDING. */
    void refreshAccounts(BankConnection connection) {
        Instant now = clock.instant();
        for (ExternalBankAccount external : provider.getBankAccounts(connection.getExternalItemId())) {
            BankAccountLink link = links.findByBankConnectionIdAndExternalAccountId(connection.getId(),
                            external.externalAccountId())
                    .orElseGet(() -> links.save(new BankAccountLink(connection.getHouseholdId(), connection.getId(),
                            external.externalAccountId())));
            link.refresh(external.name(), external.number(), external.balance(), now);
        }
    }

    BankConnection getOwned(UUID id) {
        return connections.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .filter(c -> c.getStatus() != BankConnectionStatus.DISCONNECTED)
                .orElseThrow(() -> new NotFoundException("Conexão bancária não encontrada: " + id));
    }

    String institutionName(BankConnection connection) {
        return institutions.findById(connection.getInstitutionId()).map(BankInstitution::getName).orElse("Banco");
    }

    // Saldo inicial = saldo do banco hoje − soma do que vai ser importado desde syncFrom. Assim o
    // saldo da conta no fin-mec (inicial + lançamentos) termina igual ao do banco.
    private BigDecimal initialBalanceFor(BankConnection connection, BankAccountLink link) {
        if (link.getBankBalance() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal imported = provider.getTransactions(link.getExternalAccountId(), connection.getSyncFrom()).stream()
                .filter(ExternalBankTransaction::posted)
                .map(ExternalBankTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return link.getBankBalance().subtract(imported);
    }

    private BankConnectionView view(BankConnection connection) {
        BankInstitution institution = institutions.findById(connection.getInstitutionId()).orElse(null);
        List<BankConnectionView.AccountLink> accounts = links.findAllByBankConnectionIdOrderByNameAsc(connection.getId())
                .stream()
                .map(l -> new BankConnectionView.AccountLink(l.getId(), l.getName(), l.getNumber(), l.getBankBalance(),
                        l.getBankBalanceAt(), l.getMode(), l.getAccountId()))
                .toList();
        return new BankConnectionView(connection.getId(),
                institution != null ? institution.getName() : "Banco",
                institution != null ? institution.getImageUrl() : null,
                institution != null ? institution.getPrimaryColor() : null,
                connection.getStatus(), connection.getLastError(), connection.getLastSyncedAt(),
                connection.getCreatedAt(), accounts);
    }

    // O provedor só alcança URL pública: em dev (http://localhost) não há webhook — a
    // sincronização diária e o botão "Sincronizar agora" cobrem.
    private String webhookUrl() {
        if (webhookSecret.isBlank() || !baseUrl.startsWith("https://")) {
            return null;
        }
        return baseUrl + "/api/webhooks/pluggy/" + webhookSecret;
    }

    private void requireEnabled() {
        if (!provider.isConfigured()) {
            throw new ConflictException("Integração bancária não configurada neste servidor");
        }
    }

    /** Chamada ao provedor: 404 de item vira 400 (dado do cliente); o resto vira 502. */
    private <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Conexão não encontrada no banco — tente conectar de novo");
        } catch (org.springframework.web.client.RestClientException e) {
            throw new UpstreamUnavailableException("O serviço de conexão bancária não respondeu. Tente de novo em instantes.", e);
        }
    }
}
