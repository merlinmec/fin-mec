package com.mecfin.shared.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jobs agendados (a partir da Fase 11, motor de recorrência). Desligável por propriedade para
 * rodar mais de uma instância da aplicação sem executar o job em todas - ver ROADMAP (ShedLock).
 */
@Configuration
public class SchedulingConfig {

    // Clock injetável: toda regra que depende de "hoje" fica testável sem mexer no relógio.
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "mecfin.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class EnabledScheduling {
    }
}
