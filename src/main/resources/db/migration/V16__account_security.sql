-- Fase 14: segurança da conta.

ALTER TABLE users
    -- Bloqueio temporário após falhas consecutivas de login (complementa o rate limit por
    -- IP+e-mail, que sozinho não segura um ataque distribuído por muitos IPs).
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    -- Carimbo de segurança: muda a cada troca de senha / "sair das outras sessões" / 2FA.
    -- Toda sessão guarda o carimbo do momento do login; divergiu, a sessão é derrubada.
    ADD COLUMN security_stamp UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN password_changed_at TIMESTAMPTZ,
    -- 2FA por TOTP (RFC 6238). O segredo fica cifrado com AES-256-GCM (chave fora do banco,
    -- em MECFIN_ENCRYPTION_KEY): um dump do banco sozinho não permite gerar códigos.
    ADD COLUMN totp_secret_encrypted VARCHAR(255),
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Último time-step aceito: impede reusar o mesmo código dentro da janela de 30s.
    ADD COLUMN totp_last_step BIGINT NOT NULL DEFAULT 0;

-- Códigos de recuperação do 2FA: uso único, guardados só como hash SHA-256 (são aleatórios
-- de alta entropia, não precisam de hash lento como senha).
CREATE TABLE user_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_recovery_codes_user ON user_recovery_codes(user_id);

-- Trilha de auditoria de eventos de segurança, visível ao próprio usuário (tela "Segurança").
CREATE TABLE security_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_security_events_user_created ON security_events(user_id, created_at DESC);
