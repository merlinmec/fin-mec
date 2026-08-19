package com.mecfin.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificação in-app (e-mail fica pra depois, ver roadmap). Referencia household e a origem
 * (Bill/CreditCardInvoice) só por id (nunca relação JPA), mesmo padrão do resto do projeto.
 *
 * Criada por {@code NotificationService.sync} - não tem CRUD de criação livre pelo cliente,
 * só {@link #markRead}. Diferente de {@code BillStatus}/{@code CreditCardInvoiceStatus}, aqui
 * não há estado calculado na leitura: uma vez criada, fica gravada até o usuário marcar como
 * lida (mesmo unique constraint da tabela evita duplicata pro mesmo tipo+origem).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30, updatable = false)
    private NotificationSourceType sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "message", nullable = false, length = 255, updatable = false)
    private String message;

    @Column(name = "read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            UUID householdId, NotificationType type, NotificationSourceType sourceType, UUID sourceId,
            String message) {
        this.householdId = householdId;
        this.type = type;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.message = message;
        this.read = false;
        this.createdAt = Instant.now();
    }

    // Idempotente de propósito - marcar como lida uma notificação já lida não é erro, só
    // não muda nada de novo (diferente das guardas de status "só permitido enquanto OPEN" de
    // Bill/CreditCardInvoice, aqui não há necessidade de bloquear).
    public void markRead() {
        if (read) {
            return;
        }
        this.read = true;
        this.readAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationSourceType getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
