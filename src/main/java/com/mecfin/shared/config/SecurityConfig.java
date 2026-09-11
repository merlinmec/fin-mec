package com.mecfin.shared.config;

import com.mecfin.shared.web.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Fase 0: negava tudo por padrão, liberava só o health check.
 * Fase 1 adiciona autenticação por sessão via cookie: registro/login liberados,
 * demais rotas exigem sessão autenticada; CSRF via cookie legível por JS
 * ({@link CsrfConfigurer#spa()}) para o fluxo de SPA first-party.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/actuator/health", "/api/actuator/health/**").permitAll()
                        .requestMatchers("/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                // .spa() usa CsrfTokenRequestHandler.resolveCsrfTokenValue() em modo "plain" quando o
                // header X-XSRF-TOKEN esta presente (so cai para o XOR/BREACH-safe no fallback de
                // parametro de formulario) - ou seja, o valor esperado no header e o token CRU salvo
                // no cookie, no o valor mascarado que CsrfToken#getToken() expoe (esse e so para
                // renderizar em campo de formulario escondido). Cookie com path proprio, explicito
                // como "/": nao ha mais server.servlet.context-path (ver WebConfig — a API agora usa
                // um prefixo de path em vez de context-path, justamente pra nao arrastar o cookie/o
                // resto do app pra debaixo de /api), mas o default do CookieCsrfTokenRepository ainda
                // depende do contexto da requisicao, entao mante-lo explicito evita ambiguidade.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
