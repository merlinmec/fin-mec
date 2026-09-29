-- Fase 11: motor de recorrência. Uma série guarda o "molde" do lançamento fixo e até onde
-- as ocorrências já foram materializadas em transactions; o gerador (RecurringSeriesService)
-- estende a janela aos poucos, sem nunca recriar uma ocorrência já gerada (next_index é
-- monotônico e o índice único abaixo garante idempotência mesmo sob execução concorrente).
CREATE TABLE recurring_series (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    category_id UUID REFERENCES categories(id),
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    description VARCHAR(255) NOT NULL,
    recurrence_rule VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_index INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_recurring_series_household ON recurring_series(household_id);
CREATE INDEX idx_recurring_series_active ON recurring_series(active) WHERE active;

ALTER TABLE transactions
    ADD COLUMN recurrence_series_id UUID REFERENCES recurring_series(id),
    ADD COLUMN recurrence_index INTEGER;

CREATE UNIQUE INDEX ux_transactions_recurrence_occurrence
    ON transactions(recurrence_series_id, recurrence_index)
    WHERE recurrence_series_id IS NOT NULL;

-- Lançamentos que já tinham recurrence_rule (era só metadado até a Fase 10) continuam avulsos:
-- não viram série retroativamente, para não gerar lançamentos que o usuário nunca pediu.
