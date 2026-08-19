package com.mecfin.testsupport;

import com.mecfin.identity.api.RegisterRequest;
import com.mecfin.identity.api.UserResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Registra e autentica um usuário de teste via HTTP (fluxo real de CSRF + sessão via cookie),
 * devolvendo os cookies/headers necessários para requisições autenticadas subsequentes.
 * Reutilizável por qualquer módulo futuro que precise de testes de autorização cross-user
 * (registrar dois usuários e verificar que um não acessa recurso do outro).
 */
public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public record AuthenticatedTestUser(UUID userId, String email, String sessionCookie, String csrfToken) {

        public RestTestClient.RequestHeadersSpec<?> authenticate(RestTestClient.RequestHeadersSpec<?> spec) {
            return spec
                    .cookie("JSESSIONID", sessionCookie)
                    .cookie("XSRF-TOKEN", csrfToken)
                    .header("X-XSRF-TOKEN", csrfToken);
        }
    }

    public static AuthenticatedTestUser registerAndLogin(RestTestClient client, String email, String password) {
        String bootstrapCsrfToken = fetchCsrfToken(client);

        EntityExchangeResult<UserResponse> result = client.post().uri("/auth/register")
                .cookie("XSRF-TOKEN", bootstrapCsrfToken)
                .header("X-XSRF-TOKEN", bootstrapCsrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, password))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class)
                .returnResult();

        String sessionCookie = cookieValue(result, "JSESSIONID");
        UserResponse body = result.getResponseBody();

        // O token CSRF é renovado após autenticação (CsrfAuthenticationStrategy); é preciso
        // buscar um novo via /csrf, agora já com a sessão autenticada, para usos futuros.
        String csrfToken = fetchCsrfToken(client, sessionCookie);

        return new AuthenticatedTestUser(body.id(), body.email(), sessionCookie, csrfToken);
    }

    private static String fetchCsrfToken(RestTestClient client) {
        return cookieValue(client.get().uri("/csrf").exchange().returnResult(), "XSRF-TOKEN");
    }

    private static String fetchCsrfToken(RestTestClient client, String sessionCookie) {
        return cookieValue(
                client.get().uri("/csrf").cookie("JSESSIONID", sessionCookie).exchange().returnResult(),
                "XSRF-TOKEN");
    }

    private static String cookieValue(ExchangeResult result, String name) {
        var cookie = result.getResponseCookies().getFirst(name);
        if (cookie == null) {
            throw new IllegalStateException("Expected response cookie not present: " + name);
        }
        return cookie.getValue();
    }
}
