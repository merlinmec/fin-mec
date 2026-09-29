package com.mecfin.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.mecfin.identity.api.LoginRequest;
import com.mecfin.identity.application.SessionService;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fase 18: recuperação de senha por e-mail, alerta de acesso novo e sessões no banco. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false"})
class ProductionReadinessIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final String PASSWORD = "s3cret1234";
    private static final Pattern RESET_LINK = Pattern.compile("http://localhost:5173/redefinir-senha\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbc;

    private String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String csrf() {
        return client.get().uri("/api/csrf").exchange().returnResult().getResponseCookies().getFirst("XSRF-TOKEN").getValue();
    }

    private RestTestClient.RequestBodySpec anonymousPost(String uri) {
        String token = csrf();
        return client.post().uri(uri)
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String login(String email, String password, String userAgent) {
        ExchangeResult result = anonymousPost("/api/auth/login")
                .header("User-Agent", userAgent)
                .body(new LoginRequest(email, password))
                .exchange().expectStatus().isOk().returnResult();
        return result.getResponseCookies().getFirst("JSESSIONID").getValue();
    }

    private int me(String session) {
        return client.get().uri("/api/auth/me").cookie("JSESSIONID", session).exchange().returnResult().getStatus().value();
    }

    private List<MimeMessage> mailsTo(String email) {
        return List.of(greenMail.getReceivedMessages()).stream()
                .filter(m -> recipients(m).contains(email))
                .toList();
    }

    private static String recipients(MimeMessage m) {
        try {
            return GreenMailUtil.getAddressList(m.getAllRecipients());
        } catch (jakarta.mail.MessagingException e) {
            return "";
        }
    }

    private MimeMessage awaitMail(String email, String subjectPart) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            for (MimeMessage m : mailsTo(email)) {
                if (subjectOf(m).contains(subjectPart)) {
                    return m;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("E-mail '" + subjectPart + "' não chegou para " + email);
    }

    // Texto já decodificado (quoted-printable/base64), procurando a parte text/plain dentro do
    // multipart — é o que um cliente de e-mail mostra. GreenMailUtil.getBody devolve o bruto.
    private static String textOf(jakarta.mail.Part part) {
        try {
            if (part.isMimeType("text/plain")) {
                return (String) part.getContent();
            }
            if (part.getContent() instanceof jakarta.mail.Multipart multipart) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    text.append(textOf(multipart.getBodyPart(i)));
                }
                return text.toString();
            }
            return "";
        } catch (jakarta.mail.MessagingException | java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String subjectOf(MimeMessage m) {
        try {
            return m.getSubject();
        } catch (jakarta.mail.MessagingException e) {
            return "";
        }
    }

    private String resetToken(String email) throws InterruptedException {
        Matcher matcher = RESET_LINK.matcher(textOf(awaitMail(email, "Redefinir sua senha")));
        assertThat(matcher.find()).as("link de redefinição no e-mail").isTrue();
        return matcher.group(1);
    }

    @Test
    void forgotPasswordAlwaysAnswersTheSameAndOnlyMailsRealAccounts() throws InterruptedException {
        String ghost = email();
        anonymousPost("/api/auth/password/forgot").body(Map.of("email", ghost)).exchange().expectStatus().isAccepted();
        String real = email();
        AuthTestSupport.registerAndLogin(client, real, PASSWORD);

        anonymousPost("/api/auth/password/forgot").body(Map.of("email", real.toUpperCase())).exchange()
                .expectStatus().isAccepted();

        assertThat(resetToken(real)).hasSizeGreaterThan(40);
        Thread.sleep(300);
        assertThat(mailsTo(ghost)).isEmpty();
    }

    @Test
    void resetWithTheEmailedLinkChangesThePasswordEndsSessionsAndIsSingleUse() throws InterruptedException {
        String email = email();
        AuthenticatedTestUser user = AuthTestSupport.registerAndLogin(client, email, PASSWORD);
        anonymousPost("/api/auth/password/forgot").body(Map.of("email", email)).exchange().expectStatus().isAccepted();
        String token = resetToken(email);

        anonymousPost("/api/auth/password/reset").body(Map.of("token", token, "newPassword", "1234567890"))
                .exchange().expectStatus().isBadRequest();
        anonymousPost("/api/auth/password/reset").body(Map.of("token", token, "newPassword", "nova frase secreta 2026"))
                .exchange().expectStatus().isNoContent();

        assertThat(me(user.sessionCookie())).isEqualTo(401);
        anonymousPost("/api/auth/login").body(new LoginRequest(email, PASSWORD)).exchange().expectStatus().isUnauthorized();
        login(email, "nova frase secreta 2026", "Firefox/130");
        anonymousPost("/api/auth/password/reset").body(Map.of("token", token, "newPassword", "outra frase secreta 2026"))
                .exchange().expectStatus().isBadRequest();
        assertThat(subjectOf(awaitMail(email, "foi redefinida"))).contains("foi redefinida");
    }

    @Test
    void expiredOrUnknownTokenIsRejected() throws InterruptedException {
        String email = email();
        AuthTestSupport.registerAndLogin(client, email, PASSWORD);
        anonymousPost("/api/auth/password/forgot").body(Map.of("email", email)).exchange().expectStatus().isAccepted();
        String token = resetToken(email);
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");

        anonymousPost("/api/auth/password/reset").body(Map.of("token", token, "newPassword", "nova frase secreta 2026"))
                .exchange().expectStatus().isBadRequest();
        anonymousPost("/api/auth/password/reset").body(Map.of("token", "nao-existe", "newPassword", "nova frase secreta 2026"))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void loginFromANewDeviceSendsAnAlertOnlyOnce() throws Exception {
        String email = email();
        AuthTestSupport.registerAndLogin(client, email, PASSWORD);
        // o cadastro foi feito com o User-Agent padrão do cliente de teste: o Windows já é novo
        login(email, PASSWORD, "Mozilla/5.0 (Windows NT 10.0) Chrome/130");
        assertThat(textOf(awaitMail(email, "Novo acesso"))).contains("Chrome · Windows");
        greenMail.purgeEmailFromAllMailboxes();

        login(email, PASSWORD, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Safari/604.1");
        assertThat(textOf(awaitMail(email, "Novo acesso"))).contains("Safari · iOS");

        greenMail.purgeEmailFromAllMailboxes();
        login(email, PASSWORD, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Safari/604.1");
        Thread.sleep(500);
        assertThat(mailsTo(email)).isEmpty();
    }

    @Test
    void sessionsLiveInTheDatabaseAndCanBeEndedOneByOne() {
        String email = email();
        AuthenticatedTestUser laptop = AuthTestSupport.registerAndLogin(client, email, PASSWORD);
        String phone = login(email, PASSWORD, "Mozilla/5.0 (Android 15) Chrome/130");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?", Long.class, email))
                .isEqualTo(2);
        List<SessionService.SessionInfo> sessions = laptop.authenticate(client.get().uri("/api/account/sessions"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<SessionService.SessionInfo>>() {
                })
                .returnResult().getResponseBody();
        assertThat(sessions).hasSize(2);
        assertThat(sessions).filteredOn(SessionService.SessionInfo::current).hasSize(1);
        SessionService.SessionInfo phoneSession = sessions.stream().filter(s -> !s.current()).findFirst().orElseThrow();
        assertThat(phoneSession.device()).isEqualTo("Chrome · Android");
        // o id exposto é uma impressão digital, não o id real (que é uma credencial)
        assertThat(phoneSession.id()).isNotEqualTo(phone).hasSize(24);

        AuthenticatedTestUser intruder = AuthTestSupport.registerAndLogin(client, email(), PASSWORD);
        intruder.authenticate(client.delete().uri("/api/account/sessions/" + phoneSession.id()))
                .exchange().expectStatus().isNotFound();
        assertThat(me(phone)).isEqualTo(200);

        laptop.authenticate(client.delete().uri("/api/account/sessions/" + phoneSession.id()))
                .exchange().expectStatus().isNoContent();
        assertThat(me(phone)).isEqualTo(401);
        assertThat(me(laptop.sessionCookie())).isEqualTo(200);
    }
}
