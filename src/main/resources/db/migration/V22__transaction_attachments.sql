-- Fase 20: comprovante anexado ao lançamento.
--
-- Guardado no próprio Postgres (e não em disco/objeto) de propósito: o backup de produção é um
-- pg_dump, então o comprovante entra no backup, no teste de restauração e na exclusão LGPD sem
-- nenhuma peça nova de infraestrutura. Com 5 MB por arquivo e 5 por lançamento o volume de um
-- uso pessoal/familiar cabe folgado. O acesso aos bytes passa por uma porta (AttachmentStorage),
-- então migrar para S3 depois não muda o resto do código.
--
-- Metadado e conteúdo em tabelas separadas: listar anexos nunca carrega os bytes.
CREATE TABLE transaction_attachments (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes INTEGER NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_attachments_transaction ON transaction_attachments(transaction_id);
CREATE INDEX idx_transaction_attachments_household ON transaction_attachments(household_id);

CREATE TABLE transaction_attachment_contents (
    attachment_id UUID PRIMARY KEY REFERENCES transaction_attachments(id) ON DELETE CASCADE,
    content BYTEA NOT NULL
);
