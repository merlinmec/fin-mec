package com.mecfin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MecFinApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointRespondsUp() {
        restTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody(String.class)
                .value(body -> {
                    if (body == null || !body.contains("\"status\":\"UP\"")) {
                        throw new AssertionError("Esperava status UP, obtido: " + body);
                    }
                });
    }
}
