CREATE TABLE bills (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    due_date DATE NOT NULL,
    source_account_id UUID REFERENCES accounts(id),
    category_id UUID REFERENCES categories(id),
    -- so OPEN/PAID/CANCELED sao persistidos aqui; OVERDUE e calculado na leitura
    -- (BillView.effectiveStatus) a partir de status=OPEN + due_date no passado, nunca
    -- gravado - evita job agendado so pra fazer essa transicao de estado.
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    paid_transaction_id UUID REFERENCES transactions(id),
    recurrence_rule VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- toda listagem/leitura de conta a pagar e escopada por household_id; indice cobre tambem
-- o filtro mais comum (contas ainda abertas, por vencimento).
CREATE INDEX idx_bills_household_id ON bills(household_id);
CREATE INDEX idx_bills_household_status_due_date ON bills(household_id, status, due_date);
