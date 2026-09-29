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

    public List<SecurityEvent> recent(UUID userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }
}
