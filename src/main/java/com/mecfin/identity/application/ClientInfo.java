package com.mecfin.identity.application;

// De onde veio a requisição - só para a trilha de auditoria (SecurityEvent).
public record ClientInfo(String ipAddress, String userAgent) {
}
