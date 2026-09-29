package com.mecfin.bankprovider.api;

import com.mecfin.bankprovider.application.BankSyncService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook da Pluggy. A Pluggy não assina os webhooks, então:
 * <ul>
 *   <li>a URL leva um segredo aleatório (PLUGGY_WEBHOOK_SECRET), comparado em tempo constante;</li>
 *   <li>o corpo NUNCA é usado como dado — só o itemId diz qual conexão reler, e a sincronização
 *       busca tudo de novo na API autenticada da Pluggy;</li>
 *   <li>responde na hora (a Pluggy exige 2xx em até 10 s) e sincroniza em segundo plano.</li>
 * </ul>
 * Segredo errado responde 404 (a Pluggy não reenvia 4xx e ninguém descobre que a rota existe).
 */
@RestController
@RequestMapping("/webhooks/pluggy")
public class PluggyWebhookController {

    private static final Set<String> SYNC_EVENTS = Set.of("item/created", "item/updated", "item/login_succeeded",
            "item/error", "item/waiting_user_input", "transactions/created", "transactions/updated",
            "transactions/deleted");

    private final BankSyncService syncService;
    private final byte[] secret;

    public PluggyWebhookController(BankSyncService syncService,
            @Value("${mecfin.pluggy.webhook-secret:}") String secret) {
        this.syncService = syncService;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/{secret}")
    public ResponseEntity<Void> receive(@PathVariable("secret") String provided,
            @RequestBody(required = false) Map<String, Object> body) {
        if (secret.length == 0 || !MessageDigest.isEqual(secret, provided.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.notFound().build();
        }
        if (body != null && body.get("itemId") instanceof String itemId && body.get("event") instanceof String event
                && SYNC_EVENTS.contains(event) && itemId.length() <= 100) {
            syncService.syncByItemAsync(itemId);
        }
        return ResponseEntity.accepted().build();
    }
}
