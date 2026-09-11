package com.mecfin.shared.config;

import com.mecfin.shared.web.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
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
                        // Tudo sob /api/** que nao caiu numa regra acima precisa de sessao. Fora de
                        // /api/** e o shell do SPA (index.html, JS/CSS, e qualquer rota so do client-
                        // side router tipo /contas, resolvida por WebConfig#addResourceHandlers) -
                        // sempre publico, mesmo sem sessao: e so assim que o app consegue carregar e
                        // mostrar a tela de login pra quem ainda nao entrou. A protecao de verdade
                        // continua inteira em /api/**, nunca no carregamento do bundle.
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
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
                // O CsrfFilter roda antes do ExceptionTranslationFilter na cadeia e trata a
                // propria excecao de CSRF invalido (nao deixa borbulhar) - por isso um request
                // anonimo sem cookie/token nenhum (ex.: os testes ...WithoutSession_returns401)
                // cai no CSRF antes mesmo de chegar na regra de authorizeHttpRequests, e usa o
                // accessDeniedHandler configurado aqui em vez do authenticationEntryPoint. Sem
                // esse handler customizado o resultado seria 403 (Forbidden) para quem nem
                // teria como ter um token CSRF por nunca ter se autenticado; da perspectiva do
                // cliente isso e so "preciso entrar", o mesmo sinal de uma sessao ausente -
                // entao delega pro mesmo entry point (401) quando anonimo, e mantem o 403 padrao
                // so para quem ja esta autenticado e mandou um token CSRF invalido/expirado.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler(restAuthenticationEntryPoint)))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    // Mesma logica que o ExceptionTranslationFilter aplica por padrao para AccessDeniedException
    // (redirecionar anonimo pro authenticationEntryPoint em vez do accessDeniedHandler) - precisa
    // ser replicada aqui a mao porque o CsrfFilter nunca passa pelo ExceptionTranslationFilter.
    // O CsrfFilter (posicao 5 na cadeia) roda antes do AnonymousAuthenticationFilter (posicao 9),
    // entao pra request sem sessao nenhuma o SecurityContextHolder ainda nao tem nem o
    // AnonymousAuthenticationToken de fallback - a authentication aqui e null, nao "anonima" no
    // sentido que AuthenticationTrustResolver#isAnonymous reconhece (que exige instancia nao-null
    // de AnonymousAuthenticationToken). null so acontece aqui quando nao ha sessao restaurada por
    // SecurityContextHolderFilter (posicao 3, roda antes do Csrf) - ou seja, e o mesmo caso de
    // "nao autenticado" que isAnonymous cobre pra quem ja tem AnonymousAuthenticationToken.
    private AccessDeniedHandler accessDeniedHandler(RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
        AccessDeniedHandler forbidden = new AccessDeniedHandlerImpl();
        return (request, response, deniedException) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || trustResolver.isAnonymous(authentication)) {
                restAuthenticationEntryPoint.commence(request, response,
                        new InsufficientAuthenticationException(deniedException.getMessage(), deniedException));
                return;
            }
            forbidden.handle(request, response, deniedException);
        };
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
