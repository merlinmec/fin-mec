package com.mecfin.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** No uso normal do app (sem o perfil demo), nada da demonstração existe. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoDisabledIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RestTestClient client;

    @Test
    void demoIsOff_statusFalseAndVisitorSessionNotFound() {
        client.get().uri("/api/demo/status").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.enabled").isEqualTo(false);

        String token = client.get().uri("/api/csrf").exchange().returnResult()
                .getResponseCookies().getFirst("XSRF-TOKEN").getValue();
        client.post().uri("/api/demo/session")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .exchange()
                .expectStatus().isNotFound();
    }
}
