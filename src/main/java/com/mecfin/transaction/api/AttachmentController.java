package com.mecfin.transaction.api;

import com.mecfin.transaction.application.AttachmentService;
import com.mecfin.transaction.domain.TransactionAttachment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/transactions/{transactionId}/attachments")
public class AttachmentController {

    public record AttachmentResponse(UUID id, String fileName, String contentType, int sizeBytes, UUID createdBy,
            Instant createdAt) {

        static AttachmentResponse from(TransactionAttachment a) {
            return new AttachmentResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(),
                    a.getCreatedBy(), a.getCreatedAt());
        }
    }

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable UUID transactionId) {
        return attachmentService.list(transactionId).stream().map(AttachmentResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(@PathVariable UUID transactionId, @RequestPart("file") MultipartFile file)
            throws IOException {
        return AttachmentResponse.from(
                attachmentService.upload(transactionId, file.getOriginalFilename(), file.getBytes()));
    }

    /**
     * Sempre como download (attachment) e com CSP "sandbox": conteúdo enviado por usuário nunca é
     * renderizado como página da origem do app. A pré-visualização de imagem é feita no navegador
     * a partir do blob.
     */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID transactionId, @PathVariable UUID attachmentId) {
        AttachmentService.Download download = attachmentService.download(transactionId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.attachment().getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(download.content());
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID transactionId, @PathVariable UUID attachmentId) {
        attachmentService.delete(transactionId, attachmentId);
    }
}
