package com.mecfin.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void neonConnectionString_becomesJdbcUrlUserAndPassword() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.toDatasource(
                "postgresql://neondb_owner:AbC123xyz@ep-cool-name-123456.us-west-2.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require");

        assertThat(props).containsEntry("spring.datasource.url",
                "jdbc:postgresql://ep-cool-name-123456.us-west-2.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require")
                .containsEntry("spring.datasource.username", "neondb_owner")
                .containsEntry("spring.datasource.password", "AbC123xyz");
    }

    @Test
    void neonPooledHost_isSwitchedToTheDirectEndpoint() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.toDatasource(
                "postgresql://u:p@ep-cool-name-123456-pooler.us-west-2.aws.neon.tech/neondb?sslmode=require");

        assertThat(props).containsEntry("spring.datasource.url",
                "jdbc:postgresql://ep-cool-name-123456.us-west-2.aws.neon.tech/neondb?sslmode=require");
    }

    @Test
    void keepsPortAndDecodesSpecialCharactersInPassword() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.toDatasource(
                "postgres://app:p%40ss+w%3Ard@db.example.com:6543/fin");

        assertThat(props).containsEntry("spring.datasource.url", "jdbc:postgresql://db.example.com:6543/fin")
                .containsEntry("spring.datasource.password", "p@ss+w:rd");
    }

    @Test
    void rejectsNonPostgresUrl() {
        assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.toDatasource("mysql://u:p@h/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesOnlyWhenDatabaseUrlIsSetAndNoExplicitJdbcUrl() {
        MockEnvironment withDatabaseUrl = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u:p@h/db?sslmode=require");
        processor.postProcessEnvironment(withDatabaseUrl, null);
        assertThat(withDatabaseUrl.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://h/db?sslmode=require");

        MockEnvironment explicit = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u:p@h/db")
                .withProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://outro/db");
        processor.postProcessEnvironment(explicit, null);
        assertThat(explicit.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE))
                .isFalse();

        MockEnvironment none = new MockEnvironment();
        processor.postProcessEnvironment(none, null);
        assertThat(none.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE)).isFalse();
    }
}
