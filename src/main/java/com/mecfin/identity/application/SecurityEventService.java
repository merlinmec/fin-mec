package com.mecfin.identity.application;

import com.mecfin.identity.domain.SecurityEvent;
import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.identity.infra.SecurityEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityEventService {

    private final SecurityEventRepository repository;

    public SecurityEventService(SecurityEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(UUID userId, SecurityEventType type, ClientInfo client) {
        repository.save(new SecurityEvent(userId, type, client.ipAddress(), client.userAgent()));
    }

    /**
     * Primeiro login bem-sucedido deste navegador/dispositivo? (compara o User-Agent com os logins
     * anteriores). O primeiro login da conta nunca conta como "novo" — é o cadastro.
     */
    public boolean isNewDevice(UUID userId, String userAgent) {
        if (!repository.existsByUserIdAndType(userId, SecurityEventType.LOGIN_SUCCESS)) {
            return false;
        }
        String stored = userAgent == null || userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255);
        return stored == null || !repository.existsByUserIdAndTypeAndUserAgent(userId, SecurityEventType.LOGIN_SUCCESS, stored);
    }

    public List<SecurityEvent> recent(UUID userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }
}
