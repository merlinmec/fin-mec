package com.mecfin.shared.exception;

// Arquivo acima do limite daquele recurso (o limite global de multipart é o maior deles): 413.
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
