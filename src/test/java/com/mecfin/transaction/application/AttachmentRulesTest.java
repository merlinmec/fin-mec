package com.mecfin.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentRulesTest {

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void typeComesFromTheBytesNotFromTheName() {
        assertThat(AttachmentFileType.sniff(ascii("%PDF-1.4 ..."))).contains(AttachmentFileType.PDF);
        assertThat(AttachmentFileType.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .contains(AttachmentFileType.JPEG);
        assertThat(AttachmentFileType.sniff(ascii("RIFF\0\0\0\0WEBPVP8 "))).contains(AttachmentFileType.WEBP);
        // RIFF sem WEBP é WAV/AVI, não imagem
        assertThat(AttachmentFileType.sniff(ascii("RIFF\0\0\0\0WAVEfmt "))).isEmpty();
        assertThat(AttachmentFileType.sniff(ascii("<svg onload=alert(1)>"))).isEmpty();
        assertThat(AttachmentFileType.sniff(ascii("%PD"))).isEmpty();
        assertThat(AttachmentFileType.sniff(new byte[0])).isEmpty();
    }

    @Test
    void fileNameLosesPathControlCharsAndGetsTheRealExtension() {
        assertThat(AttachmentService.fileName("C:\\Users\\x\\nota.exe", AttachmentFileType.PDF)).isEqualTo("nota.pdf");
        assertThat(AttachmentService.fileName("../../etc/passwd", AttachmentFileType.PNG)).isEqualTo("passwd.png");
        assertThat(AttachmentService.fileName("re\"ci<bo>\r\n.jpg", AttachmentFileType.JPEG)).isEqualTo("recibo.jpg");
        assertThat(AttachmentService.fileName(null, AttachmentFileType.PDF)).isEqualTo("comprovante.pdf");
        assertThat(AttachmentService.fileName(".png", AttachmentFileType.PNG)).isEqualTo("comprovante.png");
        assertThat(AttachmentService.fileName("a".repeat(300) + ".pdf", AttachmentFileType.PDF)).hasSize(104);
    }
}
