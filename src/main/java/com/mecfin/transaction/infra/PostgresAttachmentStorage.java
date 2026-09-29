package com.mecfin.transaction.infra;

import com.mecfin.transaction.application.AttachmentStorage;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Bytes em transaction_attachment_contents (BYTEA), na mesma transação do metadado. */
@Component
public class PostgresAttachmentStorage implements AttachmentStorage {

    private final JdbcTemplate jdbc;

    public PostgresAttachmentStorage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID attachmentId, byte[] content) {
        jdbc.update("INSERT INTO transaction_attachment_contents (attachment_id, content) VALUES (?, ?)",
                attachmentId, content);
    }

    @Override
    public byte[] load(UUID attachmentId) {
        return jdbc.queryForObject("SELECT content FROM transaction_attachment_contents WHERE attachment_id = ?",
                byte[].class, attachmentId);
    }

    @Override
    public void delete(UUID attachmentId) {
        jdbc.update("DELETE FROM transaction_attachment_contents WHERE attachment_id = ?", attachmentId);
    }
}
