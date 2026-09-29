# fin-mec — Roadmap pós-MVP

Atualizado em 29/09/2026. Documento vivo: o que o fin-mec entrega hoje, como se
compara aos apps de finanças pessoais de referência e o que vem a seguir, com
critério de pronto por fase.

## Referências de mercado

Comparação feita com base no conhecimento público dos produtos (sem integração
nem acesso a contas reais):

| Capacidade | Organizze | Mobills | YNAB | Monarch / Copilot | Actual / Firefly III | **fin-mec** |
|---|---|---|---|---|---|---|
| Contas, lançamentos, transferências, parcelas | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Lançamentos fixos gerados automaticamente | ✔ | ✔ | ✔ (scheduled) | ✔ | ✔ | ✔ Fase 11 |
| Cartão de crédito com fatura | ✔ | ✔ | parcial | ✔ | parcial | ✔ Fase 8 |
| Orçamento por categoria | ✔ | ✔ | ✔ (envelope) | ✔ | ✔ | ✔ Fase 4 |
| Metas de economia | parcial | ✔ | ✔ | ✔ | ✔ (piggy banks) | ✔ Fase 13 |
| Tags | ✔ | ✔ | ✔ (flags) | ✔ | ✔ | ✔ Fase 13 |
| Relatórios (fluxo, categorias, evolução) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ Fase 12 |
| Busca e exportação | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ Fase 12 |
| 2FA | ✔ | ✔ | ✔ | ✔ | ✔ (Firefly) | ✔ Fase 14 |
| Importação OFX/CSV | ✔ | ✔ | ✔ | ✔ | ✔ | ✘ Fase 15 |
| Regras de categorização automática | parcial | ✔ | ✔ | ✔ | ✔ | ✘ Fase 15 |
| Open Finance (sincronização bancária) | ✔ | ✔ | ✔ (EUA) | ✔ | ✘ | só a porta (Fase 9) → Fase 16 |
| Conta compartilhada (casal/família) | ✔ | ✔ | ✔ | ✔ | ✘ | schema pronto → Fase 17 |
| Recuperação de senha por e-mail | ✔ | ✔ | ✔ | ✔ | ✔ | ✘ Fase 18 |
| App mobile / PWA | nativo | nativo | nativo | nativo | PWA | responsivo → Fase 20 |

**Onde o fin-mec já se diferencia** (argumento de portfólio): toda regra
financeira derivada no backend e coberta por testes de integração contra
Postgres real (Testcontainers); motor de recorrência idempotente com janela
deslizante; 2FA implementado sobre o RFC 6238 e validado contra os vetores do
próprio RFC; revogação de sessão por carimbo de segurança; exclusão de conta
LGPD com inventário auditável das tabelas; gráficos com paleta validada para
daltonismo.

## Entregue nesta rodada (Fases 11–14 + FE-10)

- **Fase 11, motor de recorrência:** séries, janela de 12 meses, job diário,
  efetivar lançamento previsto, editar ou cancelar "este e os próximos", conta
  a pagar recorrente, previsão de saldo considerando o que está previsto.
- **Fase 12, relatórios e busca:** fluxo de caixa, evolução do saldo, ranking
  por categoria com variação contra o período anterior, busca textual segura,
  filtros por data e valor, exportação CSV à prova de *formula injection*.
- **Fase 13, metas e tags.**
- **Fase 14, segurança:** 2FA TOTP, códigos de recuperação, bloqueio por
  tentativas, revogação de sessões, política de senha (NIST 800-63B),
  auditoria, cabeçalhos (CSP, HSTS…), exclusão de conta. Corrigidos no caminho:
  *session fixation* no login e Swagger exposto em produção.
- **FE-10:** paleta de comandos, lançamento rápido, tema escuro, navegação
  mobile com botão flutuante, dashboard com gráficos, telas de relatórios,
  metas e configurações.

## Próximas fases

### Fase 15: Importação de extrato (OFX/CSV) + regras automáticas
**Objetivo:** reduzir o lançamento manual, que é o maior motivo de abandono de
apps de finanças.
- Upload de OFX (padrão dos bancos brasileiros) e CSV com mapeamento de
  colunas; prévia antes de gravar.
- Detecção de duplicados (FITID do OFX; data + valor + descrição normalizada
  no CSV) e conciliação com lançamentos previstos, o que efetiva o fixo em vez
  de duplicar.
- Regras "se a descrição contém X → categoria Y / tag Z", aplicadas na
  importação e sugeridas a partir do histórico.
- **Pronto quando:** importar o mesmo arquivo duas vezes não cria nada novo; um
  extrato real de 3 bancos diferentes importa sem erro; limite de tamanho e
  validação de tipo no upload.

### Fase 16: Open Finance de verdade (completa a Fase 9)
- Adapter Pluggy para o `BankProviderClient` já existente, webhook assinado,
  sincronização incremental reaproveitando a deduplicação da Fase 15.
- Segredos só em variável de ambiente, token de conexão cifrado com o
  `SecretCipher`.
- **Pronto quando:** conta sandbox sincroniza, reconecta após expirar o
  consentimento e nunca duplica lançamento.

### Fase 17: Household compartilhado
- Convite por e-mail com token de uso único, papéis OWNER/MEMBER já
  modelados, "quem lançou" em cada lançamento, transferência de posse antes de
  excluir a conta do dono (hoje a exclusão só remove a participação).
- **Pronto quando:** dois usuários veem os mesmos dados; membro removido perde
  acesso na hora (reaproveitar o carimbo de segurança).

### Fase 18: Produção (a Fase 11 do roadmap original)
- Deploy com HTTPS (HSTS já configurado), `server.forward-headers-strategy`
  atrás do proxy (hoje o IP da auditoria e do rate limit seria o do proxy).
- E-mail transacional: **recuperação de senha** (lacuna de confiabilidade mais
  importante hoje) e alerta de login em dispositivo novo.
- Spring Session JDBC: lista de sessões ativas com "encerrar esta", sessões
  sobrevivendo a deploy e escala horizontal.
- ShedLock no job de recorrência (hoje há `mecfin.scheduling.enabled` para
  ligar em uma instância só).
- Backup automático do Postgres com teste de restauração; observabilidade
  (métricas do Actuator, logs estruturados com o correlation id que já existe).
- **Pronto quando:** restore testado, alerta de erro 5xx, zero segredo no
  repositório.

### Fase 19: Inteligência
- Previsão de saldo para 30/60/90 dias a partir dos fixos (curva, não só o fim
  do mês).
- Alertas: gasto de categoria acima da média, orçamento a 80%, fatura acima do
  normal.
- Resumo mensal automático ("você guardou 18% da renda em setembro").

### Fase 20: Mobile e conveniência
- PWA instalável com cache offline das telas de leitura; atalho de lançamento
  rápido na tela inicial.
- Anexo de comprovante no lançamento (armazenamento em objeto, varredura de
  tipo e tamanho).
- Portabilidade LGPD completa: exportar tudo em JSON (a exclusão já existe).

## Limitações conhecidas (hoje)

- Conta a pagar recorrente avança a data a partir do vencimento anterior: uma
  conta do dia 31 passa a vencer no 30/28 depois de meses curtos, e não volta
  (o motor de lançamentos fixos não tem esse problema).
- Tags de um lançamento fixo criado antes da Fase 13 não são retroativas.
- Revogação de sessão entre várias instâncias leva até 10 s (cache do carimbo);
  resolvido de vez com o Spring Session da Fase 18.
- O bloqueio por tentativas permite que alguém que conheça seu e-mail bloqueie
  a conta por 15 minutos. É o trade-off padrão: a mitigação futura é
  CAPTCHA/prova de trabalho após algumas falhas.
