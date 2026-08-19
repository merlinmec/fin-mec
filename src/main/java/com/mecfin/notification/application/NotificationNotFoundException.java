package com.mecfin.notification.application;

import com.mecfin.shared.exception.NotFoundException;
import java.util.UUID;

public class NotificationNotFoundException extends NotFoundException {

    public NotificationNotFoundException(UUID id) {
        super("Notificação não encontrada: " + id);
    }
}
