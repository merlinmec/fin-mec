CREATE TABLE households (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE household_members (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (household_id, user_id)
);

-- Resolve o household de um usuário no login (UserDetailsServiceImpl) sem varrer a tabela toda.
CREATE INDEX idx_household_members_user_id ON household_members(user_id);
