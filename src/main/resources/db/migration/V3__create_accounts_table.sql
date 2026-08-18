CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    initial_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    archived BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Toda listagem/leitura de conta é escopada por household_id e exclui soft-deleted;
-- índice parcial cobre exatamente essa consulta.
CREATE INDEX idx_accounts_household_id ON accounts(household_id) WHERE deleted_at IS NULL;
