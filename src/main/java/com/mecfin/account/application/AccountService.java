package com.mecfin.account.application;

import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import com.mecfin.account.infra.AccountRepository;
import com.mecfin.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account create(String name, AccountType type, BigDecimal initialBalance) {
        Account account = new Account(CurrentUser.householdId(), name, type, initialBalance);
        return accountRepository.save(account);
    }

    public List<Account> list() {
        return accountRepository.findAllByHouseholdIdAndDeletedAtIsNullOrderByNameAsc(CurrentUser.householdId());
    }

    public Account get(UUID id) {
        return accountRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(id, CurrentUser.householdId())
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional
    public Account update(UUID id, String name, AccountType type, boolean archived) {
        Account account = get(id);
        account.update(name, type, archived);
        return account;
    }

    @Transactional
    public void delete(UUID id) {
        get(id).softDelete();
    }
}
