package com.mecfin.shared.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Aceita o banco no formato que os provedores entregam (Neon, Render, Heroku...):
 * {@code DATABASE_URL=postgresql://usuario:senha@host/banco?sslmode=require}. O driver JDBC não
 * entende usuário e senha dentro da URL, então este processador separa as partes em
 * {@code spring.datasource.url/username/password} antes de o Spring montar o pool de conexões.
 * Assim, configurar a demonstração pública é colar uma linha, sem montar URL JDBC à mão.
 *
 * <p>Só age com {@code DATABASE_URL} definida e sem {@code spring.datasource.url} explícita: no uso
 * normal (DB_HOST, DB_NAME...) nada muda. Registrado em META-INF/spring.factories.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE = "databaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank() || environment.containsProperty("SPRING_DATASOURCE_URL")) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, toDatasource(databaseUrl)));
    }

    static Map<String, Object> toDatasource(String databaseUrl) {
        URI uri = URI.create(databaseUrl.trim());
        String scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) {
            throw new IllegalArgumentException("DATABASE_URL precisa começar com postgresql:// (veio " + scheme + "://)");
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(directHost(uri.getHost()));
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath());
        if (uri.getRawQuery() != null) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", jdbc.toString());
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            properties.put("spring.datasource.username", decode(colon < 0 ? userInfo : userInfo.substring(0, colon)));
            if (colon >= 0) {
                properties.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return properties;
    }

    /**
     * O Neon oferece o mesmo banco por um pooler (PgBouncer em modo transação, host com "-pooler"):
     * as travas de sessão do Flyway e o pool próprio da aplicação não combinam com ele. Trocar
     * para o endpoint direto é tirar o "-pooler" do host. Assim, qualquer das duas strings que o
     * painel do Neon mostrar funciona.
     */
    static String directHost(String host) {
        return host.replace("-pooler.", ".");
    }

    // Senha com caracteres especiais vem com percent-encoding (%40 = @). Não é form-encoding: "+"
    // é literal, por isso o "+" é protegido antes do URLDecoder.
    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
