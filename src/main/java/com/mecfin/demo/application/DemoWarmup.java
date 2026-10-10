package com.mecfin.demo.application;

import com.mecfin.identity.application.VisitorAccountService;
import com.mecfin.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Logo depois de subir, cria uma conta de visitante, preenche e apaga. No plano gratuito (0,1 CPU) a
 * primeira criação levava ~15 s só por a JVM ainda estar "fria"; com o caminho todo já executado
 * uma vez, o primeiro visitante de verdade cai nos ~5 s das seguintes.
 *
 * <p>Roda numa thread própria para não atrasar a subida (o health check do Render já responde) e,
 * se falhar, só registra: a demo funciona igual, apenas mais lenta na primeira vez.
 */
@Component
// mecfin.demo.warmup=false nos testes de integração: rodando em paralelo, mexeria nas contagens.
@ConditionalOnExpression("${mecfin.demo.enabled:false} and ${mecfin.demo.warmup:true}")
public class DemoWarmup {

    private static final Logger log = LoggerFactory.getLogger(DemoWarmup.class);

    private final VisitorAccountService visitors;
    private final DemoDataSeeder seeder;
    private final UserDetailsService userDetailsService;

    public DemoWarmup(VisitorAccountService visitors, DemoDataSeeder seeder, UserDetailsService userDetailsService) {
        this.visitors = visitors;
        this.seeder = seeder;
        this.userDetailsService = userDetailsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.ofVirtual().name("demo-warmup").start(this::warmUp);
    }

    /** @return se o aquecimento foi até o fim (falha só é registrada: a demo segue funcionando). */
    public boolean warmUp() {
        long start = System.nanoTime();
        User visitor = null;
        try {
            visitor = visitors.create();
            // O seeder escopa tudo pelo household do usuário autenticado: aqui não há requisição,
            // então a autenticação é montada à mão só nesta thread.
            UserDetails principal = userDetailsService.loadUserByUsername(visitor.getEmail());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
            seeder.seed();
            log.info("Demonstração aquecida em {} ms", (System.nanoTime() - start) / 1_000_000);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Aquecimento da demonstração falhou (a demo segue funcionando)", ex);
            return false;
        } finally {
            SecurityContextHolder.clearContext();
            if (visitor != null) {
                try {
                    visitors.delete(visitor.getId());
                } catch (RuntimeException ex) {
                    log.warn("Não consegui apagar a conta de aquecimento; a limpeza horária apaga", ex);
                }
            }
        }
    }
}
