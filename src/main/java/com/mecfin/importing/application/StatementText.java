package com.mecfin.importing.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decodifica o arquivo em texto. Extrato de banco brasileiro chega em UTF-8 ou em
 * Windows-1252 (padrão de OFX 1.x, "CHARSET:1252"): tenta UTF-8 estrito e, se o arquivo não for
 * UTF-8 válido, cai para Windows-1252 — assim "Pão de Açúcar" nunca vira "PÃ£o de AÃ§Ãºcar".
 */
final class StatementText {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private StatementText() {
    }

    static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer)
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, offset, bytes.length - offset, WINDOWS_1252);
        }
    }
}
