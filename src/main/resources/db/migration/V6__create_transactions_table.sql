CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    category_id UUID REFERENCES categories(id),
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    description VARCHAR(255) NOT NULL,
    transaction_date DATE NOT NULL,
    competence_month DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- household_id nao e coluna propria (resolvido via account.household_id, ver arquitetura em
-- generic-rolling-lemur.md secao F); toda listagem por household passa antes pela lista de
-- account_id do household (AccountService.householdAccountIds()).
CREATE INDEX idx_transactions_account_id ON transactions(account_id);

-- suporta o calculo de gasto realizado por categoria/mes usado pelo modulo budget.
CREATE INDEX idx_transactions_category_competence ON transactions(category_id, competence_month)
    WHERE status = 'POSTED';
