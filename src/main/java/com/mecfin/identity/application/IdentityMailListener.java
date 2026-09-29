package com.mecfin.identity.application;

import com.mecfin.shared.mail.EmailSender;
import com.mecfin.shared.mail.EmailTemplates;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * E-mails da identidade. AFTER_COMMIT: o link de redefinição só sai se o token foi mesmo
 * gravado; @Async: SMTP lento não segura a resposta (e o tempo de resposta de "esqueci a senha"
 * não revela se o e-mail existe).
 */
@Component
public class IdentityMailListener {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final EmailSender emailSender;

    public IdentityMailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        String text = """
                Recebemos um pedido para redefinir a senha da sua conta no fin-mec.

                Para criar uma nova senha, abra o link abaixo (vale por %d minutos e só funciona uma vez):
                %s

                Se não foi você, ignore este e-mail: sua senha continua a mesma.
                """.formatted(event.validMinutes(), event.resetUrl());
        String html = EmailTemplates.layout("Redefinir sua senha",
                EmailTemplates.paragraph("Recebemos um pedido para redefinir a senha da sua conta.")
                        + EmailTemplates.paragraph("O link vale por " + event.validMinutes()
                                + " minutos e só funciona uma vez. Se não foi você, ignore este e-mail."),
                "Criar nova senha", event.resetUrl());
        emailSender.send(event.email(), "Redefinir sua senha do fin-mec", text, html);
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onSecurityAlert(SecurityAlertEvent event) {
        String when = WHEN.format(event.when());
        String subject;
        String lead;
        if (event.kind() == SecurityAlertEvent.Kind.NEW_DEVICE_LOGIN) {
            subject = "Novo acesso à sua conta do fin-mec";
            lead = "Sua conta foi acessada de um dispositivo que ainda não tínhamos visto.";
        } else {
            subject = "Sua senha do fin-mec foi redefinida";
            lead = "A senha da sua conta foi redefinida e todas as sessões abertas foram encerradas.";
        }
        String details = "Quando: " + when + " · Dispositivo: " + event.device()
                + (event.ipAddress() != null ? " · IP: " + event.ipAddress() : "");
        String advice = "Se foi você, está tudo certo. Se não foi, redefina sua senha agora e ative a "
                + "verificação em duas etapas em Configurações > Segurança.";
        String text = lead + "\n\n" + details + "\n\n" + advice + "\n";
        String html = EmailTemplates.layout(subject,
                EmailTemplates.paragraph(lead) + EmailTemplates.paragraph(details) + EmailTemplates.paragraph(advice),
                null, null);
        emailSender.send(event.email(), subject, text, html);
    }
}
