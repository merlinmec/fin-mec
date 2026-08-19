CREATE TABLE credit_cards (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    name VARCHAR(120) NOT NULL,
    credit_limit NUMERIC(19,4) NOT NULL,
    closing_day SMALLINT NOT NULL CHECK (closing_day BETWEEN 1 AND 31),
    due_day SMALLINT NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    payment_account_id UUID REFERENCES accounts(id),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_credit_cards_household_id ON credit_cards(household_id);

-- Cobrancas do cartao (credit_card_charges, abaixo) ficam numa tabela propria em vez de
-- reaproveitar "transactions" (diferente do que o doc de arquitetura original previa): as
-- queries de Budget (TransactionRepository.sumAmount/sumGroupedByCategory) filtram por
-- "account_id IN (contas do household)", entao uma linha de cobranca de cartao sem account_id
-- simplesmente nao seria contada ali - e reintroduzir esse calculo por household_id de cartao
-- arriscaria duplicidade quando a fatura fosse paga (a cobranca original + a Transaction da
-- baixa). Mesmo padrao ja validado em Bill: fica numa tabela propria ate a baixa, que ai sim
-- cria uma Transaction real via TransactionService (ver credit_card_invoices.paid_transaction_id).
CREATE TABLE credit_card_invoices (
    id UUID PRIMARY KEY,
    credit_card_id UUID NOT NULL REFERENCES credit_cards(id),
    reference_month DATE NOT NULL,
    closing_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    paid_transaction_id UUID REFERENCES transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_card_invoices_card_month UNIQUE (credit_card_id, reference_month)
);

CREATE INDEX idx_credit_card_invoices_credit_card_id ON credit_card_invoices(credit_card_id);

-- type reaproveita o mesmo dominio de transactions.type (INCOME/EXPENSE) - sem FK/enum
-- compartilhado no banco (mesmo padrao do resto do projeto), so no mapeamento JPA
-- (com.mecfin.transaction.domain.TransactionType), evitando duplicar o enum.
CREATE TABLE credit_card_charges (
    id UUID PRIMARY KEY,
    credit_card_invoice_id UUID NOT NULL REFERENCES credit_card_invoices(id),
    category_id UUID REFERENCES categories(id),
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    description VARCHAR(255) NOT NULL,
    purchase_date DATE NOT NULL,
    installment_number INT,
    installment_total INT,
    installment_group_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_credit_card_charges_invoice_id ON credit_card_charges(credit_card_invoice_id);
