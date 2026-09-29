package com.mecfin.identity.application;

// Publicado dentro da transação; o e-mail só sai depois do commit (IdentityMailListener).
public record PasswordResetRequestedEvent(String email, String resetUrl, int validMinutes) {
}
