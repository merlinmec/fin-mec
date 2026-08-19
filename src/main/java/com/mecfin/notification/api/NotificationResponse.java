package com.mecfin.notification.api;

import com.mecfin.notification.domain.Notification;
import com.mecfin.notification.domain.NotificationSourceType;
import com.mecfin.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationSourceType sourceType,
        UUID sourceId,
        String message,
        boolean read,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getType(), notification.getSourceType(),
                notification.getSourceId(), notification.getMessage(), notification.isRead(),
                notification.getReadAt(), notification.getCreatedAt());
    }
}
