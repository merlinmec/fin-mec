package com.mecfin.identity.application;

import java.time.Instant;

/** Alerta de segurança por e-mail: acesso de dispositivo novo, senha redefinida. */
public record SecurityAlertEvent(Kind kind, String email, String device, String ipAddress, Instant when) {

    public enum Kind {
        NEW_DEVICE_LOGIN,
        PASSWORD_RESET
    }
}
