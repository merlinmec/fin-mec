CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    reference_month DATE NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- um unico orcamento por categoria/mes/household; reference_month sempre normalizado
    -- para o primeiro dia do mes (ver Budget.java).
    UNIQUE (household_id, category_id, reference_month)
);

CREATE INDEX idx_budgets_household_id ON budgets(household_id);
