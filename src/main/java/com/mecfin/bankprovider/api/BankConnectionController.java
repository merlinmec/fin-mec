package com.mecfin.bankprovider.api;

import com.mecfin.bankprovider.application.BankConnectionService;
import com.mecfin.bankprovider.application.BankConnectionView;
import com.mecfin.bankprovider.application.BankSyncService;
import com.mecfin.bankprovider.application.SyncResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/bank-connections")
public class BankConnectionController {

    public record ConnectTokenRequest(UUID connectionId) {
    }

    public record RegisterRequest(@NotBlank @Size(max = 100) String itemId) {
    }

    public record SetupRequest(@NotNull BankConnectionService.SetupMode mode, UUID accountId) {
    }

    private final BankConnectionService connectionService;
    private final BankSyncService syncService;

    public BankConnectionController(BankConnectionService connectionService, BankSyncService syncService) {
        this.connectionService = connectionService;
        this.syncService = syncService;
    }

    @GetMapping("/status")
    public BankConnectionService.Status status() {
        return connectionService.status();
    }

    @PostMapping("/connect-token")
    public BankConnectionService.ConnectToken connectToken(@RequestBody(required = false) ConnectTokenRequest request) {
        return connectionService.connectToken(request == null ? null : request.connectionId());
    }

    @GetMapping
    public List<BankConnectionView> list() {
        return connectionService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankConnectionView register(@Valid @RequestBody RegisterRequest request) {
        return connectionService.register(request.itemId());
    }

    @PutMapping("/{id}/accounts/{linkId}")
    public BankConnectionView setupAccount(@PathVariable UUID id, @PathVariable UUID linkId,
            @Valid @RequestBody SetupRequest request) {
        return connectionService.setupAccount(id, linkId, request.mode(), request.accountId());
    }

    @PostMapping("/{id}/sync")
    public SyncResult sync(@PathVariable UUID id) {
        return syncService.syncOwned(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID id) {
        connectionService.disconnect(id);
    }
}
