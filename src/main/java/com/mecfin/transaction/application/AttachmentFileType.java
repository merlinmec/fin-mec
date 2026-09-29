package com.mecfin.transaction.application;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos aceitos como comprovante, reconhecidos pelos primeiros bytes do arquivo — nunca pelo
 * Content-Type nem pela extensão que o navegador manda (ambos são o que o cliente quiser). Um
 * HTML renomeado para "nota.pdf" não passa.
 */
public enum AttachmentFileType {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp");

    private final String mediaType;
    private final String extension;

    AttachmentFileType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<AttachmentFileType> sniff(byte[] content) {
        if (startsWith(content, 0, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<AttachmentFileType> fromMediaType(String mediaType) {
        return Arrays.stream(values()).filter(t -> t.mediaType.equals(mediaType)).findFirst();
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
