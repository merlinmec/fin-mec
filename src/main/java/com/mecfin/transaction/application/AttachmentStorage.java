package com.mecfin.transaction.application;

import java.util.UUID;

/**
 * Onde moram os bytes dos comprovantes (Fase 20). Hoje: Postgres (entra no pg_dump do backup).
 * Trocar por S3/objeto é escrever outro adapter — metadado, regras e API não mudam.
 */
public interface AttachmentStorage {

    void save(UUID attachmentId, byte[] content);

    byte[] load(UUID attachmentId);

    void delete(UUID attachmentId);
}
