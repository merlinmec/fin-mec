package com.mecfin.transaction.application;

import com.mecfin.shared.exception.ConflictException;
import com.mecfin.shared.exception.NotFoundException;
import com.mecfin.shared.exception.PayloadTooLargeException;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionAttachment;
import com.mecfin.transaction.infra.TransactionAttachmentRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprovantes de lançamento (Fase 20). Regras: até {@value #MAX_BYTES} bytes e
 * {@value #MAX_PER_TRANSACTION} por lançamento; tipo reconhecido pelo conteúdo (PDF, PNG, JPEG,
 * WEBP); nome do arquivo saneado e com a extensão do tipo real. O lançamento precisa ser do
 * household (TransactionService.get já responde 404 para o de outro).
 */
@Service
public class AttachmentService {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_PER_TRANSACTION = 5;

    public record Download(TransactionAttachment attachment, byte[] content) {
    }

    private final TransactionService transactionService;
    private final TransactionAttachmentRepository attachments;
    private final AttachmentStorage storage;

    public AttachmentService(TransactionService transactionService, TransactionAttachmentRepository attachments,
            AttachmentStorage storage) {
        this.transactionService = transactionService;
        this.attachments = attachments;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<TransactionAttachment> list(UUID transactionId) {
        transactionService.get(transactionId);
        return attachments.findAllByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    @Transactional
    public TransactionAttachment upload(UUID transactionId, String originalName, byte[] content) {
        Transaction transaction = transactionService.get(transactionId);
        if (content.length == 0) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        if (content.length > MAX_BYTES) {
            throw new PayloadTooLargeException("O comprovante pode ter até 5 MB");
        }
        AttachmentFileType type = AttachmentFileType.sniff(content).orElseThrow(() ->
                new IllegalArgumentException("Envie PDF, PNG, JPEG ou WEBP"));
        if (attachments.countByTransactionId(transactionId) >= MAX_PER_TRANSACTION) {
            throw new ConflictException("Cada lançamento aceita até " + MAX_PER_TRANSACTION + " comprovantes");
        }
        TransactionAttachment attachment = attachments.save(new TransactionAttachment(CurrentUser.householdId(),
                transaction.getId(), fileName(originalName, type), type.mediaType(), content.length, sha256(content),
                CurrentUser.id()));
        attachments.flush();
        storage.save(attachment.getId(), content);
        return attachment;
    }

    @Transactional(readOnly = true)
    public Download download(UUID transactionId, UUID attachmentId) {
        TransactionAttachment attachment = owned(transactionId, attachmentId);
        return new Download(attachment, storage.load(attachment.getId()));
    }

    @Transactional
    public void delete(UUID transactionId, UUID attachmentId) {
        TransactionAttachment attachment = owned(transactionId, attachmentId);
        storage.delete(attachment.getId());
        attachments.delete(attachment);
    }

    private TransactionAttachment owned(UUID transactionId, UUID attachmentId) {
        transactionService.get(transactionId);
        return attachments.findByIdAndTransactionId(attachmentId, transactionId)
                .orElseThrow(() -> new NotFoundException("Comprovante não encontrado"));
    }

    /**
     * Só o nome-base, sem caminho nem caracteres de controle, até 100 caracteres, e sempre com a
     * extensão do tipo REAL — "nota.exe" que na verdade é um PDF vira "nota.pdf".
     */
    static String fileName(String original, AttachmentFileType type) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        int dot = name.lastIndexOf('.');
        // ".png" é só extensão; pontos no começo virariam arquivo oculto em alguns sistemas.
        String base = (dot >= 0 ? name.substring(0, dot) : name).replaceFirst("^\\.+", "").trim();
        if (base.isEmpty()) {
            base = "comprovante";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        return base + "." + type.extension();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
