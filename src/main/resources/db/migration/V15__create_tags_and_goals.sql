-- Fase 13: tags livres em lançamentos + metas de economia.

CREATE TABLE tags (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Nome único por household sem diferenciar maiúsculas ("Viagem" e "viagem" são a mesma tag).
CREATE UNIQUE INDEX ux_tags_household_name ON tags(household_id, LOWER(name));

-- Associação N:N. ON DELETE CASCADE nos dois lados: excluir uma tag só a remove dos
-- lançamentos (nunca exclui lançamento), e lançamento nunca é hard-deletado mesmo.
CREATE TABLE transaction_tags (
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, tag_id)
);

CREATE INDEX idx_transaction_tags_tag ON transaction_tags(tag_id);

-- Meta = "cofrinho" virtual: acumula aportes registrados pelo usuário, não movimenta conta.
-- saved/percentual/quanto falta por mês são sempre derivados dos aportes na leitura.
CREATE TABLE goals (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    name VARCHAR(120) NOT NULL,
    target_amount NUMERIC(19,4) NOT NULL CHECK (target_amount > 0),
    target_date DATE,
    color VARCHAR(7),
    icon VARCHAR(50),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_goals_household ON goals(household_id);

-- amount negativo = resgate. A regra "saldo da meta nunca fica negativo" é do serviço.
CREATE TABLE goal_contributions (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    amount NUMERIC(19,4) NOT NULL CHECK (amount <> 0),
    contribution_date DATE NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_goal_contributions_goal ON goal_contributions(goal_id);

-- Tags do molde de um lançamento fixo: toda ocorrência gerada (inclusive pelo job diário,
-- meses depois) nasce com as mesmas tags.
CREATE TABLE recurring_series_tags (
    series_id UUID NOT NULL REFERENCES recurring_series(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (series_id, tag_id)
);
