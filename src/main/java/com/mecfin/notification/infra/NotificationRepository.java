package com.mecfin.notification.infra;

import com.mecfin.notification.domain.Notification;
import com.mecfin.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByHouseholdIdOrderByCreatedAtDesc(UUID householdId);

    List<Notification> findAllByHouseholdIdAndReadOrderByCreatedAtDesc(UUID householdId, boolean read);

    // Chave de dedup do sync (ver uq_notifications_household_type_source) - só cria uma nova
    // notificação quando ainda não existe uma daquele tipo exato pra aquela origem.
    Optional<Notification> findByHouseholdIdAndTypeAndSourceId(UUID householdId, NotificationType type, UUID sourceId);

    // household_id faz parte do filtro (não só do "where exists"): garante que uma notificação
    // de outro household nunca é encontrada, mesmo que adivinhada (mitigação IDOR/BOLA).
    Optional<Notification> findByIdAndHouseholdId(UUID id, UUID householdId);
}
