-- (household_id, type, source_id) e unico de proposito: NotificationService so cria uma
-- notificacao nova quando ainda nao existe uma daquele tipo exato pra aquela conta a
-- pagar/fatura - evita spam ao re-sincronizar todo dia enquanto o lancamento continua
-- pendente. Quando o status muda (ex.: DUE_SOON -> OVERDUE), type muda junto, entao uma nova
-- linha e permitida (informacao nova de verdade).
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    type VARCHAR(40) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id UUID NOT NULL,
    message VARCHAR(255) NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_household_type_source UNIQUE (household_id, type, source_id)
);

CREATE INDEX idx_notifications_household_id ON notifications(household_id);
