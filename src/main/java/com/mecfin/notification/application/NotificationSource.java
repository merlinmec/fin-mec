package com.mecfin.notification.application;

import com.mecfin.notification.domain.NotificationSourceType;
import com.mecfin.notification.domain.NotificationType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Porta para outros módulos contribuírem alertas ao sino (Fase 19). A notificação não precisa
 * conhecer orçamento, relatório ou cartão para gerar "orçamento a 80%": quem sabe calcular
 * implementa esta interface, e o {@link NotificationService#sync()} só grava o que ainda não
 * existe (tipo + origem + período). Roda no contexto do usuário (CurrentUser disponível).
 */
public interface NotificationSource {

    record Candidate(NotificationType type, NotificationSourceType sourceType, UUID sourceId, String periodKey,
            String message) {
    }

    List<Candidate> candidates(LocalDate today);
}
