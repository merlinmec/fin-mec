package com.mecfin.notification.api;

import com.mecfin.notification.application.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Gera as notificações pendentes (contas a pagar/faturas vencendo ou vencidas) e devolve a
    // lista já atualizada - ver NotificationService.sync sobre por que isso é sob demanda em
    // vez de um job agendado.
    @PostMapping("/sync")
    public List<NotificationResponse> sync() {
        return notificationService.sync().stream().map(NotificationResponse::from).toList();
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(required = false) Boolean read) {
        return notificationService.list(read).stream().map(NotificationResponse::from).toList();
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return NotificationResponse.from(notificationService.markRead(id));
    }
}
