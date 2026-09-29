package com.mecfin.household.application;

import com.mecfin.household.domain.HouseholdInviteCreatedEvent;
import com.mecfin.shared.mail.EmailSender;
import com.mecfin.shared.mail.EmailTemplates;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** E-mail de convite: sai só depois do commit (o convite existe) e fora da requisição. */
@Component
public class HouseholdMailListener {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final EmailSender emailSender;

    public HouseholdMailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onInviteCreated(HouseholdInviteCreatedEvent event) {
        String who = event.invitedByEmail() != null ? event.invitedByEmail() : "Alguém";
        String lead = who + " convidou você para compartilhar as finanças no fin-mec (\"" + event.householdName()
                + "\").";
        String details = "Vocês vão ver e lançar nas mesmas contas, cartões, orçamentos e metas. O convite vale até "
                + DAY.format(event.expiresAt()) + " e só funciona com a conta deste e-mail.";
        String text = lead + "\n\n" + details + "\n\nAceitar o convite: " + event.acceptUrl()
                + "\n\nSe você não esperava este convite, ignore este e-mail.\n";
        String html = EmailTemplates.layout("Convite para o fin-mec",
                EmailTemplates.paragraph(lead) + EmailTemplates.paragraph(details)
                        + EmailTemplates.paragraph("Se você não esperava este convite, ignore este e-mail."),
                "Ver convite", event.acceptUrl());
        emailSender.send(event.email(), who + " convidou você para o fin-mec", text, html);
    }
}
