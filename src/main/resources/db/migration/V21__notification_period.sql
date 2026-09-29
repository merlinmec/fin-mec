-- Fase 19: alertas inteligentes. Um alerta de "categoria acima da média" ou de orçamento é
-- informação nova a cada mês para a MESMA origem (categoria/orçamento), então a unicidade que
-- evita spam passa a incluir o período. Alertas de vencimento (Fase 10) continuam com período
-- vazio: a origem (conta/fatura) já é única no tempo.
ALTER TABLE notifications
    ADD COLUMN period_key VARCHAR(20) NOT NULL DEFAULT '';

ALTER TABLE notifications
    DROP CONSTRAINT uq_notifications_household_type_source,
    ADD CONSTRAINT uq_notifications_household_type_source_period
        UNIQUE (household_id, type, source_id, period_key);
