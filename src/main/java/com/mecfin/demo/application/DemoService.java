package com.mecfin.demo.application;

import com.mecfin.identity.application.VisitorAccountService;
import com.mecfin.identity.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Demonstração pública (botão "Testar" do portfólio): cada visitante ganha uma conta temporária
 * própria, com dados fictícios, que some depois de {@code mecfin.demo.ttl}. Contas separadas, e não
 * uma conta compartilhada, porque um visitante não pode bagunçar o que o próximo vê.
 *
 * <p>Só existe com {@code mecfin.demo.enabled=true} (deploy da demo). No uso normal do app não há
 * nada disto carregado.
 */
@Service
@ConditionalOnProperty(name = "mecfin.demo.enabled", havingValue = "true")
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final VisitorAccountService visitors;
    private final DemoDataSeeder seeder;
    private final Clock clock;
    private final Duration ttl;
    private final int maxActive;

    public DemoService(VisitorAccountService visitors, DemoDataSeeder seeder, Clock clock,
            @Value("${mecfin.demo.ttl:PT24H}") Duration ttl,
            @Value("${mecfin.demo.max-active:300}") int maxActive) {
        this.visitors = visitors;
        this.seeder = seeder;
        this.clock = clock;
        this.ttl = ttl;
        this.maxActive = maxActive;
    }

    /**
     * Cria a conta do visitante. A limpeza roda antes, e não só no job: no plano gratuito o servidor
     * dorme quando ninguém usa, então o job agendado pode passar dias sem rodar.
     */
    public User createVisitor() {
        removeExpired();
        if (visitors.countActive() >= maxActive) {
            throw new DemoFullException();
        }
        return visitors.create();
    }

    /** Preenche a conta já autenticada na requisição; se falhar, a conta pela metade é apagada. */
    public void seed(UUID visitorId) {
        try {
            seeder.seed();
        } catch (RuntimeException ex) {
            visitors.delete(visitorId);
            throw ex;
        }
    }

    @Scheduled(cron = "${mecfin.demo.cleanup-cron:0 0 * * * *}")
    @SchedulerLock(name = "demo-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void removeExpired() {
        var expired = visitors.createdBefore(clock.instant().minus(ttl));
        for (UUID id : expired) {
            visitors.delete(id);
        }
        if (!expired.isEmpty()) {
            log.info("Demonstração: {} conta(s) de visitante expirada(s) apagada(s)", expired.size());
        }
    }
}
