package com.mecfin.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import com.mecfin.account.infra.AccountRepository;
import com.mecfin.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Mock
    private AccountRepository accountRepository;

    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void authenticateAsHousehold() {
        AuthenticatedPrincipal principal = new TestPrincipal(UUID.randomUUID(), householdId);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createScopesNewAccountToCurrentHousehold() {
        AccountService service = new AccountService(accountRepository);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account account = service.create("Conta Corrente", AccountType.CHECKING, BigDecimal.TEN);

        assertThat(account.getHouseholdId()).isEqualTo(householdId);
        assertThat(account.getName()).isEqualTo("Conta Corrente");
    }

    @Test
    void listReturnsOnlyAccountsOfCurrentHousehold() {
        AccountService service = new AccountService(accountRepository);
        Account account = new Account(householdId, "Poupança", AccountType.SAVINGS, BigDecimal.ZERO);
        when(accountRepository.findAllByHouseholdIdAndDeletedAtIsNullOrderByNameAsc(householdId))
                .thenReturn(List.of(account));

        List<Account> accounts = service.list();

        assertThat(accounts).containsExactly(account);
    }

    @Test
    void getThrowsAccountNotFoundWhenAbsentOrFromAnotherHousehold() {
        AccountService service = new AccountService(accountRepository);
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(accountId, householdId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(accountId)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deleteSoftDeletesAccount() {
        AccountService service = new AccountService(accountRepository);
        UUID accountId = UUID.randomUUID();
        Account account = new Account(householdId, "Carteira", AccountType.WALLET, BigDecimal.ZERO);
        when(accountRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(accountId, householdId))
                .thenReturn(Optional.of(account));

        service.delete(accountId);

        assertThat(account.getDeletedAt()).isNotNull();
    }
}
