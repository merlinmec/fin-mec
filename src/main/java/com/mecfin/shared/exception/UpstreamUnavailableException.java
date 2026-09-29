package com.mecfin.shared.exception;

// Serviço externo (ex.: provedor de Open Finance) falhou ou não respondeu: 502, não 500 — o
// problema não é deste servidor, e o cliente pode mostrar "tente de novo em instantes".
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
