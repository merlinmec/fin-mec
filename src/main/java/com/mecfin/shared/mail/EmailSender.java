package com.mecfin.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envio de e-mail transacional (Fase 18). Sem SMTP configurado (spring.mail.host vazio — o
 * normal em desenvolvimento sem o Mailpit de pé), não falha: registra no log o assunto e o
 * corpo, para o link de redefinição de senha continuar testável localmente.
 *
 * Nunca lança exceção para o chamador: e-mail é efeito colateral assíncrono (ver
 * IdentityMailListener) e uma falha de SMTP não pode desfazer nem travar a operação principal.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final boolean configured;

    // MAIL_HOST vazio ainda faz o Spring Boot criar um JavaMailSender (a propriedade existe, só
    // está em branco) — por isso "configurado" é decidido pelo valor do host, não pelo bean.
    public EmailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${mecfin.mail.from}") String from,
            @Value("${spring.mail.host:}") String host) {
        this.mailSender = mailSender;
        this.from = from;
        this.configured = !host.isBlank();
    }

    public void send(String to, String subject, String text, String html) {
        JavaMailSender sender = configured ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.warn("SMTP não configurado — e-mail NÃO enviado. Para: {} | Assunto: {}\n{}", to, subject, text);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("Falha ao enviar e-mail '{}' para {}", subject, to, e);
        }
    }
}
