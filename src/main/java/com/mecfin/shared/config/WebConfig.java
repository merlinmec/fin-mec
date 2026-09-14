package com.mecfin.shared.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Duas responsabilidades da topologia API (/api) + SPA (/) no mesmo processo
 * Spring Boot:
 *
 * <p>1) Prefixa todo endpoint REST (qualquer {@code @RestController}) com
 * /api, sem precisar prefixar cada {@code @RequestMapping} individualmente.
 * Substitui o antigo {@code server.servlet.context-path: /api} (removido do
 * application.yml) — um context-path desloca literalmente TUDO no
 * ServletContext, inclusive a resolução de recursos estáticos, então
 * {@code GET /} nunca chegava a este app (404 do próprio Tomcat, antes mesmo
 * do Spring). Com o prefixo aplicado só aos controllers REST, a raiz "/" fica
 * livre pra servir o SPA compilado, enquanto a API continua em /api/* —
 * exatamente a topologia que a FE-0 (#20) pretendia, mas context-path não
 * conseguia entregar.
 *
 * <p>SecurityConfig, os testes de integração (RestTestClient não resolve mais
 * o prefixo automaticamente — cada .uri() nos testes agora escreve /api
 * explicitamente) e management.endpoints.web.base-path (application.yml,
 * main E teste — essa property não é herdada pelo bean, precisou ser
 * replicada nos dois) precisaram ser atualizados junto — nenhum deles herda
 * esse prefixo automaticamente, cada um resolve o path da requisição por
 * conta própria.
 *
 * <p>2) Fallback de rota do SPA em {@code src/main/resources/static}
 * (populado pelo build do frontend, ver pom.xml) — ver comentário no método
 * abaixo.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class));
    }

    // Fallback de rota do SPA: React Router é client-side, então uma navegação
    // GET pra uma rota só dele (ex.: /contas num hard refresh) não existe como
    // arquivo em src/main/resources/static — sem isso, viraria 404 em vez de
    // carregar o SPA, que só então resolveria a rota no browser. Qualquer
    // caminho sob /api/** ou /actuator/** é explicitamente excluído (devolve
    // null, cai no 404 normal do Spring) pra uma chamada de API inválida nunca
    // virar HTML por engano. Se src/main/resources/static estiver vazio (dev
    // local sem rodar o build do frontend, ver pom.xml), o resolver não acha
    // nem o arquivo nem o index.html e some no 404 padrão — não quebra nada.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
