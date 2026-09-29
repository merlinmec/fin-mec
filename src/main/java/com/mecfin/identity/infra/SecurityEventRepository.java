package com.mecfin.identity.infra;

import com.mecfin.identity.domain.SecurityEvent;
import com.mecfin.identity.domain.SecurityEventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    List<SecurityEvent> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndType(UUID userId, SecurityEventType type);

    boolean existsByUserIdAndTypeAndUserAgent(UUID userId, SecurityEventType type, String userAgent);
}
