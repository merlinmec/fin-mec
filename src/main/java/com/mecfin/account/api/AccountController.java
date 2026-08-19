package com.mecfin.account.api;

import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(request.name(), request.type(), request.initialBalance());
        return AccountResponse.from(account);
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accountService.list().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id));
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request) {
        Account account = accountService.update(id, request.name(), request.type(), request.archived());
        return AccountResponse.from(account);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        accountService.delete(id);
    }
}
