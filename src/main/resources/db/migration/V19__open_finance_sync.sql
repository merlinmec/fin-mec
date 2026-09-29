-- Fase 16: Open Finance de verdade (adapter Pluggy para a porta BankProviderClient da Fase 9).

-- Visual da instituição no app (logo e cor vêm do provedor).
ALTER TABLE bank_institutions
    ADD COLUMN image_url VARCHAR(500),
    ADD COLUMN primary_color VARCHAR(20);

-- Motivo legível da última falha (ex.: "Credenciais inválidas no banco — reconecte").
ALTER TABLE bank_connections
    ADD COLUMN last_error VARCHAR(255),
    ADD COLUMN sync_from DATE;

-- Cada conta do banco conectado e a conta do fin-mec que recebe os lançamentos dela.
-- account_id nulo + mode PENDING = o usuário ainda não escolheu (criar conta nova, vincular a uma
-- existente ou ignorar). A sincronização só importa contas com mode LINKED.
CREATE TABLE bank_account_links (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    bank_connection_id UUID NOT NULL REFERENCES bank_connections(id),
    external_account_id VARCHAR(255) NOT NULL,
    account_id UUID REFERENCES accounts(id),
    mode VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    name VARCHAR(255) NOT NULL,
    number VARCHAR(60),
    bank_balance NUMERIC(19,4),
    bank_balance_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bank_account_links_external UNIQUE (bank_connection_id, external_account_id)
);

CREATE INDEX idx_bank_account_links_household ON bank_account_links(household_id);

-- import_batches.format ganha BANK_SYNC (cada sincronização vira um lote, com "desfazer").
