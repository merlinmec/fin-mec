package com.mecfin.identity.application;

// 400 via GlobalExceptionHandler (IllegalArgumentException): é erro de dado de entrada.
public class WeakPasswordException extends IllegalArgumentException {

    public WeakPasswordException(String message) {
        super(message);
    }
}
