-- Fase 17: household compartilhado (convite, papéis, "quem lançou").

-- Um usuário participa de exatamente um household por vez. O login sempre assumiu isso
-- (findHouseholdIdByUserId); agora que existe convite, o banco garante.
ALTER TABLE household_members
    ADD CONSTRAINT uq_household_members_user UNIQUE (user_id);

-- Convite de uso único, preso ao e-mail convidado. Só o hash SHA-256 do token fica no banco
-- (mesmo padrão da redefinição de senha): quem lê o banco não consegue aceitar convite nenhum.
CREATE TABLE household_invites (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    invited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    accepted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_household_invites_household ON household_invites(household_id);

-- Quem lançou. Nulo = criado pelo sistema (sincronização bancária, job de recorrência) ou por
-- alguém que já excluiu a conta.
ALTER TABLE transactions
    ADD COLUMN created_by UUID REFERENCES users(id) ON DELETE SET NULL;
