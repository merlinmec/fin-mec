package com.mecfin.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prefixa todo endpoint REST (qualquer {@code @RestController}) com /api, sem
 * precisar prefixar cada {@code @RequestMapping} individualmente. Substitui o
 * antigo {@code server.servlet.context-path: /api} (removido do
 * application.yml) — um context-path desloca literalmente TUDO no
 * ServletContext, inclusive a resolução de recursos estáticos, então
 * {@code GET /} nunca chegava a este app (404 do próprio Tomcat, antes mesmo
 * do Spring). Com o prefixo aplicado só aos controllers REST, a raiz "/" fica
 * livre pra servir o SPA compilado (ver pom.xml e
 * {@code src/main/resources/static}), enquanto a API continua em /api/* —
 * exatamente a topologia que a FE-0 (#20) pretendia, mas context-path não
 * conseguia entregar.
 *
 * SecurityConfig, os testes de integração (RestTestClient não resolve mais o
 * prefixo automaticamente — cada .uri() nos testes agora escreve /api
 * explicitamente) e management.endpoints.web.base-path (application.yml)
 * precisaram ser atualizados junto — nenhum deles herda esse prefixo
 * automaticamente, cada um resolve o path da requisição por conta própria.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
