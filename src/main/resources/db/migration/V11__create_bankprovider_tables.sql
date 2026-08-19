-- Fase 9 (escopo reduzido, decidido com o usuário): so a fronteira do modulo bankprovider -
-- schema + dominio + a porta BankProviderClient (ver com.mecfin.bankprovider.application),
-- sem nenhum adapter concreto (Pluggy/Belvo) ainda. Tabelas ficam vazias ate a Fase 9 "de
-- verdade" (quando houver credencial de sandbox) - nenhum service/controller as usa por
-- enquanto, mesmo espirito do que o doc de arquitetura ja prescrevia pra essa fase.

-- Catalogo de instituicoes (bancos) suportadas pelo provedor - populado por
-- BankProviderClient.listInstitutions() quando um adapter concreto existir, nao por seed
-- manual (diferente de categories: aqui o catalogo pertence ao provedor externo, nao ao
-- produto).
CREATE TABLE bank_institutions (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    provider_code VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bank_institutions_provider_code UNIQUE (provider_code)
);

-- access_token_encrypted fica sempre cifrado pela aplicacao (AES-GCM, chave fora do banco) -
-- nunca token em texto claro nessa coluna; a cifra e responsabilidade do adapter concreto
-- (BankProviderClient.createConnection), nao existe ainda enquanto so a porta esta implementada.
CREATE TABLE bank_connections (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    institution_id UUID NOT NULL REFERENCES bank_institutions(id),
    provider VARCHAR(60) NOT NULL,
    external_item_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    access_token_encrypted TEXT,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- idempotencia de conexao: reprocessar a mesma conexao de um provedor nunca duplica a linha.
    CONSTRAINT uq_bank_connections_provider_item UNIQUE (provider, external_item_id)
);

CREATE INDEX idx_bank_connections_household_id ON bank_connections(household_id);
