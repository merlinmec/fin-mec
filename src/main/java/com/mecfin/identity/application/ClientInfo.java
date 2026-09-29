package com.mecfin.identity.application;

// De onde veio a requisição - só para a trilha de auditoria (SecurityEvent).
public record ClientInfo(String ipAddress, String userAgent) {

    /** Evento sem requisição do próprio usuário (ex.: removido do household pelo dono). */
    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);
}
