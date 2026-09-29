package com.mecfin.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// E-mail sai de forma assíncrona (IdentityMailListener): o SMTP lento ou fora do ar não segura a
// resposta HTTP. Usa o executor de tarefas autoconfigurado do Spring Boot.
@Configuration
@EnableAsync
public class AsyncConfig {
}
