package com.mecfin.shared.mail;

import org.springframework.web.util.HtmlUtils;

/**
 * Layout HTML mínimo e compatível com clientes de e-mail (tabela + estilos inline, sem CSS
 * externo nem imagem). Todo texto dinâmico passa por HtmlUtils.htmlEscape — o user-agent de um
 * alerta de login, por exemplo, vem do navegador de quem logou e não é confiável.
 */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static String layout(String title, String bodyHtml, String buttonLabel, String buttonUrl) {
        String button = buttonUrl == null ? "" : """
                <p style="margin:28px 0"><a href="%s" style="background:#0f7e33;color:#ffffff;text-decoration:none;
                padding:12px 22px;border-radius:8px;font-weight:600;display:inline-block">%s</a></p>
                """.formatted(HtmlUtils.htmlEscape(buttonUrl), HtmlUtils.htmlEscape(buttonLabel));
        return """
                <!doctype html><html lang="pt-BR"><body style="margin:0;background:#f4f6f8;font-family:Arial,sans-serif;color:#1f2937">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 16px">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:14px;padding:32px">
                <tr><td>
                <p style="margin:0 0 20px;font-size:18px;font-weight:700;color:#0f7e33">fin-mec</p>
                <h1 style="margin:0 0 12px;font-size:20px">%s</h1>
                %s
                %s
                <p style="margin:28px 0 0;font-size:12px;color:#6b7280">Você recebeu este e-mail porque tem uma conta no fin-mec.
                Nunca pedimos sua senha por e-mail.</p>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(HtmlUtils.htmlEscape(title), bodyHtml, button);
    }

    public static String paragraph(String text) {
        return "<p style=\"margin:0 0 12px;line-height:1.55\">" + HtmlUtils.htmlEscape(text) + "</p>";
    }
}
