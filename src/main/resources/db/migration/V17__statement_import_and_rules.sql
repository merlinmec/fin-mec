-- Fase 15: importação de extrato (OFX/CSV) + regras de categorização automática.

-- Um lote por arquivo importado: histórico e "desfazer importação".
CREATE TABLE import_batches (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    file_name VARCHAR(255) NOT NULL,
    format VARCHAR(10) NOT NULL,
    created_count INTEGER NOT NULL,
    matched_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    undone_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_import_batches_household ON import_batches(household_id, created_at DESC);

-- external_id: identificador da linha no extrato (FITID do OFX; hash determinístico da linha
-- no CSV). O índice único por conta é o que torna a importação idempotente: reimportar o
-- mesmo arquivo nunca cria lançamento repetido, mesmo com duas abas/requisições ao mesmo tempo.
ALTER TABLE transactions
    ADD COLUMN external_id VARCHAR(120),
    ADD COLUMN import_batch_id UUID REFERENCES import_batches(id);

CREATE UNIQUE INDEX ux_transactions_account_external
    ON transactions(account_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX idx_transactions_import_batch ON transactions(import_batch_id)
    WHERE import_batch_id IS NOT NULL;

-- "Se a descrição contém X, use a categoria Y (e a tag Z)". pattern é guardado já
-- normalizado (minúsculo, sem acento) para casar com a descrição normalizada do extrato.
CREATE TABLE categorization_rules (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    pattern VARCHAR(100) NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    tag_id UUID REFERENCES tags(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_categorization_rules_household_pattern ON categorization_rules(household_id, pattern);
