CREATE TABLE categories (
    id UUID PRIMARY KEY,
    household_id UUID REFERENCES households(id),
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    parent_id UUID REFERENCES categories(id),
    color VARCHAR(7),
    icon VARCHAR(50),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- household_id nulo = categoria padrão do sistema, visível a todo household.
-- Toda listagem combina "minhas categorias" + "categorias padrão", excluindo soft-deleted.
CREATE INDEX idx_categories_household_id ON categories(household_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_categories_parent_id ON categories(parent_id) WHERE deleted_at IS NULL;
