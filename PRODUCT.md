# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Hoje: usuário único (dono do household) gerenciando as próprias finanças
pessoais. Arquitetura já modelada para household compartilhado (papéis
OWNER/MEMBER) — convite de um segundo membro (ex.: casal) é feature adiada,
schema já suporta.

## Product Purpose

Centralizador pessoal de vida financeira: contas manuais, lançamentos
(receita/despesa/transferência, com parcelamento e recorrência-metadado),
categorias, orçamento por categoria/mês, contas a pagar, cartão de crédito
+ fatura, notificações de vencimento e dashboard com saldo contábil/
disponível e previsão simples.

## Positioning

[Inferido, não confirmado nesta sessão] Projeto pessoal com ambição de
portfólio técnico: réplica funcional do modelo de dados/fluxos do
Organizze (produto de referência escolhido pelo usuário), mas com stack e
arquitetura próprias construídas do zero (Spring Boot modular monolith +
React), sem vínculo comercial com o Organizze.

## Operating Context

Uso via navegador, desktop e mobile (responsivo desde a FE-9). Sessão via
cookie HttpOnly + CSRF. Sem integração bancária real ainda — Fase 9 do
roadmap entregou só a porta/schema (`BankProviderClient`), sem adapter.
Build de produção: jar único (Spring Boot) servindo API (`/api/**`) e o
SPA React estático.

## Capabilities and Constraints

Backend: Java 21 + Spring Boot, Postgres, monólito modular por domínio
(household, account, category, transaction, budget, bill, creditcard,
bankprovider, dashboard, notification). Frontend: React 19 + TypeScript +
Vite, Tailwind v4, shadcn/ui (componentes copiados pro repo, não pacote
externo), TanStack Query, cliente HTTP escrito à mão (sem codegen).
Dashboard e outras telas de agregação **nunca recalculam no frontend** —
sempre consomem valores já prontos da API (percentageUsed de orçamento,
status efetivo de Bill/Invoice, etc.) — restrição de arquitetura que o
redesign visual não deve violar.

## Brand Commitments

Nome do projeto: **fin-mec**. [Confirmado nesta sessão] A inspiração
visual no Organizze deve ficar restrita à linguagem de UI (paleta, cards,
iconografia, topologia de navegação) — não copiar o logo/wordmark
"organizze" nem se apresentar como o produto real; fin-mec mantém nome e
identidade próprios.

## Evidence on Hand

Capturas de tela reais da página de marketing do Organizze
(organizze.com.br), baixadas nesta sessão, usadas como referência de
linguagem visual (não de conteúdo/copy/dados):
`hero-maior.png` (dashboard desktop + app mobile lado a lado),
`recursos.png` (app mobile, tela de saldo geral).
Mostram: header/topbar verde vibrante com nav horizontal, cards brancos
com sombra suave sobre fundo levemente cinza, saldo com ícone de olho pra
ocultar valor, lista de contas com ícone circular colorido por
instituição + valor à direita, receita em verde/despesa em vermelho,
bottom tab bar mobile com FAB verde central.

## Product Principles

1. Números financeiros sempre confiáveis: nunca recalcular no frontend o
   que o backend já manda pronto; cor com significado consistente (verde
   = positivo/receita, vermelho = negativo/despesa) em todo o app, não só
   no dashboard.
2. Mobile-first pragmático: o app já é responsivo (FE-9); o redesign não
   pode regredir isso — testar em largura de telefone é parte do
   critério de pronto, não um extra.
3. Um usuário só hoje, mas a UI não deve fechar a porta pro household
   compartilhado (ex.: nada hardcoded assumindo "dono único" na
   navegação/labels).
4. Projeto pessoal com padrão de acabamento de portfólio: preferir poucos
   componentes bem feitos e reutilizados (mesmo padrão já estabelecido:
   CategorySelect, AccountSelect, MonthSelector) a duplicar UI por tela.

## Accessibility & Inclusion

Nenhum requisito específico de acessibilidade foi levantado além do
padrão razoável (contraste de texto, foco visível, `aria-label` em ícones
sem texto — já presentes no código atual, ex. `AppShell`).
