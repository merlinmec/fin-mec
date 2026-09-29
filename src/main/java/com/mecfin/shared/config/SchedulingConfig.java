package com.mecfin.shared.config;

import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jobs agendados (a partir da Fase 11, motor de recorrência). Desde a Fase 18 cada job leva um
 * @SchedulerLock (ShedLock, tabela shedlock): com várias réplicas da aplicação, só uma executa
 * cada disparo. mecfin.scheduling.enabled continua existindo para desligar os jobs de vez.
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
    @EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
    @ConditionalOnProperty(name = "mecfin.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class EnabledScheduling {

        // usingDbTime: a hora da trava vem do Postgres, não do relógio de cada instância.
        @Bean
        LockProvider lockProvider(DataSource dataSource) {
            return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                    .withJdbcTemplate(new JdbcTemplate(dataSource))
                    .usingDbTime()
                    .build());
        }
    }
}
