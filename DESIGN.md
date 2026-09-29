---
name: fin-mec
description: Centralizador pessoal de finanças, com a topologia operacional do Organizze
colors:
  brand-green: "oklch(0.52 0.145 148)"
  brand-green-foreground: "oklch(0.99 0 0)"
  page-bg: "oklch(0.977 0.004 247)"
  surface: "oklch(1 0 0)"
  surface-foreground: "oklch(0.208 0.021 256)"
  neutral-muted: "oklch(0.958 0.006 248)"
  neutral-muted-foreground: "oklch(0.554 0.028 257)"
  border: "oklch(0.925 0.011 256)"
  positive: "oklch(0.6 0.16 149)"
  negative: "oklch(0.577 0.245 27)"
  warning: "oklch(0.769 0.16 70)"
typography:
  body:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica Neue, Arial, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  stat-value:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica Neue, Arial, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.2
rounded:
  sm: "0.375rem"
  md: "0.5rem"
  lg: "0.75rem"
  xl: "1.25rem"
  full: "9999px"
spacing:
  sm: "0.5rem"
  md: "1rem"
  lg: "1.5rem"
components:
  button-primary:
    backgroundColor: "{colors.brand-green}"
    textColor: "{colors.brand-green-foreground}"
    rounded: "{rounded.md}"
    padding: "0.5rem 1rem"
  button-primary-hover:
    backgroundColor: "{colors.brand-green}"
  button-outline:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.surface-foreground}"
    rounded: "{rounded.md}"
    padding: "0.5rem 1rem"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.surface-foreground}"
    rounded: "{rounded.xl}"
    padding: "1rem"
  stat-tile-accent:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.xl}"
    padding: "1rem"
---

# Design System: fin-mec

## Overview

**Creative North Star: "O Organizze com crachá próprio"**

fin-mec adota a topologia operacional de um produto real do domínio de
finanças pessoais — o Organizze — sem tomar emprestado seu nome, logo ou
wordmark. A UI existia desde a FE-0 como um skeleton shadcn neutro
(slate + teal, sidebar fixa): funcional, mas genérico, sem nenhuma
identidade de marca. Este redesign (FE-9.1) substitui esse skeleton por
uma linguagem mais confiante: verde como cor de marca em vez de acento
discreto, nav horizontal em vez de sidebar, ícones circulares coloridos
por conta/categoria, cards com sombra fazendo a separação visual (não
borda), e números financeiros com hierarquia clara — porque em um app de
dinheiro, os números SÃO o produto.

Rejeitado deliberadamente: qualquer coisa que faça o fin-mec parecer o
Organizze de verdade (mesmo logo, mesma paleta pixel-a-pixel, mesmo nome).
A inspiração é de topologia e linguagem visual, não clonagem.

**Key Characteristics:**
- Verde vibrante como cor de marca, usado sem timidez (topbar inteira,
  não só um botão de destaque).
- Fundo da página levemente acinzentado; cards brancos com sombra suave
  fazem o conteúdo "flutuar" sobre o fundo.
- Números financeiros grandes e com cor semântica consistente em todo o
  app (verde = receita/positivo, vermelho = despesa/negativo).
- Identidade visual "por entidade": toda conta e toda categoria tem um
  ícone circular colorido — nunca uma lista de texto puro.

## Colors

Paleta restrita: um verde de marca (dobra como cor primária de ação E cor
de topbar/nav), vermelho pra despesa/erro, e neutros. Sem paleta
secundária/terciária — o verde é a única cor "de marca" no sistema.

### Primary
- **Verde fin-mec** (`oklch(0.52 0.145 148)` ≈ `#0F7E33`): cor de marca.
  Usada na topbar inteira (fundo), em todo botão de ação primária, no
  anel de foco, e no acento lateral do stat tile de saldo principal.
  Contraste vs. texto branco: 5.16:1 (AA para texto normal).

### Neutral
- **Fundo de página** (`oklch(0.977 0.004 247)`): fundo de todo o app
  fora dos cards — deliberadamente não-branco, pra que os cards brancos
  com sombra tenham contra o que "flutuar".
- **Superfície de card** (`oklch(1 0 0)`, branco puro): fundo de todo
  card, dialog, popover.
- **Texto secundário / labels** (`oklch(0.554 0.028 257)`): labels de
  stat tile, subtítulos, texto de apoio.
- **Borda** (`oklch(0.925 0.011 256)`): usada em opacidade reduzida
  (`/60`) nos cards — a sombra faz a separação principal, a borda é só
  reforço sutil.

### Semantic
- **Positivo/receita** (`oklch(0.6 0.16 149)` ≈ `#1B9946`): mesmo hue do
  verde de marca (148 vs 149) — a marca inteira é "verde", não dois
  verdes competindo. Contraste vs. card branco: 3.68:1 (passa AA pra
  texto grande — é sempre usado em `text-2xl font-semibold` ou maior).
- **Negativo/despesa** (`oklch(0.577 0.245 27)`, vermelho): saldo
  negativo, despesa do mês, erros de formulário.

### Named Rules
**The One Green Rule.** Existe um único verde no sistema (hue ~148).
Brand, ação primária e "positivo financeiro" compartilham a mesma
família de cor — nunca introduzir um segundo verde (ex.: um teal ou um
verde-água "diferente") pra outro propósito.

## Typography

**Body Font:** system-ui stack (sem fonte customizada — texto financeiro
denso se beneficia da fonte nativa da plataforma, que o usuário já lê
bem, em vez de uma fonte de exibição).

**Character:** utilitária e direta — a hierarquia vem de peso e tamanho,
não de troca de família tipográfica.

### Hierarchy
- **Stat value** (font-semibold, 1.5rem/24px, line-height 1.2): o número
  grande de cada `StatTile` — saldo, receita, despesa. É o elemento mais
  proeminente de qualquer tela que o usa.
- **Título de página** (font-semibold, 1.25rem/20px, tracking-tight):
  `<h1>` de cada rota.
- **Body** (400, 0.875rem/14px, line-height 1.5): texto corrido, labels
  de formulário, conteúdo de tabela/lista.
- **Label** (0.75rem/12px, cor `neutral-muted-foreground`): legendas
  abaixo de valores (ex.: "contábil: R$ X" abaixo do saldo disponível).

## Layout

Container principal com `max-width` de `72rem` (max-w-6xl) centralizado,
padding `1rem` (mobile) a `1.5rem` (sm+). Topbar full-width (sem max-width
— a cor de marca vai até a borda da viewport), sticky no topo.

**Responsivo:** a nav horizontal só existe a partir de `lg` (1024px). Abaixo
disso, os itens de nav somem da topbar e um botão de hambúrguer abre um
menu suspenso (dropdown abaixo da topbar, não uma gaveta lateral) —
mudança deliberada da FE-9 original, que usava uma sidebar-gaveta; a
topologia agora é "tudo no topo", então o menu mobile também vem de cima.

Grids de stat tile: 2 colunas no mobile, 5 no desktop (`lg:grid-cols-5`).
Grids de card de conteúdo (dashboard, orçamento): 1 coluna no mobile, 2 no
desktop.

## Elevation & Depth

Sistema de sombra simples, um único nível: `shadow-sm` em todo card,
combinado com uma borda muito sutil (`border-border/60`) só como reforço
de borda em telas de alto contraste/impressão — a sombra é que faz o
trabalho de separar o card do fundo cinza da página, a borda sozinha não
seria suficiente porque `--border` é próximo demais de `--background`.

### Named Rules
**The Shadow-Over-Border Rule.** Card não se separa do fundo por borda
pesada; separa por sombra + fundo branco puro contra um fundo de página
levemente cinza. Uma borda visível competindo com a sombra é redundância,
não reforço.

## Shapes

Radius generoso e consistente: `0.75rem` (`--radius`, usado como `xl` em
cards/dialogs) — mais arredondado que o `0.625rem` original da FE-0, pra
casar com o caráter "amigável" do Organizze. Ícones de conta/categoria
são sempre círculos completos (`rounded-full`), nunca quadrados
arredondados — é o que os diferencia visualmente de um card comum.

## Components

### Buttons
- **Shape:** `rounded-md` (0.5rem).
- **Primary:** fundo `--primary` (verde), texto branco, `hover:bg-primary/90`.
- **Outline:** usado nas ações da topbar (ex.: "Sair") sobre o fundo
  verde — nesse contexto específico, a borda vira branca translúcida
  (`border-white/30`) e o hover é `bg-white/10`, não o par
  outline/accent padrão (que assume fundo claro).
- **Ghost (ícones da topbar):** herda `text-primary-foreground` do
  `<header>` por cascata de `color`; hover força `bg-white/15` em vez do
  `hover:bg-accent` padrão (que renderizaria um cinza claro sobre o
  verde — errado).

### Cards / Containers
- **Corner Style:** `rounded-2xl` (1.25rem).
- **Background:** sempre branco puro (`bg-card`), nunca o cinza de
  fundo da página.
- **Shadow Strategy:** `shadow-sm` (ver Elevation & Depth) + borda
  `border-border/60` como reforço, não como separação primária.
- **Internal Padding:** `1rem` (`p-4`) no caso comum; `1.5rem` (`p-6`)
  nos cards "cheios" de tela isolada (login/registro).

### Entity Icon (conta / categoria) — componente de assinatura
Círculo de `size-6` (categoria, dentro de listas densas) a `size-9`
(conta, mais proeminente), fundo colorido sólido, ícone lucide branco
centralizado. Categoria usa a cor cadastrada pelo usuário
(`Category.color`, obrigatória via color picker); conta não tem campo de
cor no backend, então a cor é derivada deterministicamente do `id` da
conta (hash simples → paleta curada de 10 tons) — nunca aleatória entre
renders, sempre a mesma conta = sempre a mesma cor. É o elemento que mais
aproxima o fin-mec do Organizze visualmente: nenhuma lista de
conta/categoria deveria aparecer como texto puro, sempre com esse círculo
à esquerda.

### Stat Tile
- **Shape:** `rounded-2xl`, mesmo padrão de card.
- **Accent:** o stat "lead" de um grid (ex.: "Saldo disponível" no
  dashboard) ganha `border-l-4 border-primary` — acento de borda lateral
  verde, decisão pinada pela referência real do Organizze (que usa
  exatamente esse traço nos cards "Saldo geral"/"Faturas de Maio"); os
  demais stats do mesmo grid não usam o acento, pra manter só um "líder"
  visual por grid.
- **Maskable:** quando a tile representa saldo (não receita/despesa/
  previsão), aceita um botão de olho que troca o valor por `••••••` —
  preferência persistida em `localStorage`, compartilhada entre todas as
  tiles maskable da tela (um único toggle global, não um por tile).

### Navigation
Topbar full-bleed com fundo `--primary`. Links horizontais (`rounded-full`,
texto `primary-foreground/80`, ativo = `bg-white/15` + texto sólido) só
em `lg+`. Abaixo disso, um menu suspenso abre por baixo da topbar com os
mesmos links empilhados verticalmente.

## Do's and Don'ts

### Do:
- **Do** usar o círculo colorido (`AccountIcon`/`CategoryIcon`) em toda
  lista de conta ou categoria — nunca listar como texto puro.
- **Do** manter verde e vermelho como as únicas cores com significado
  financeiro fixo (positivo/negativo) em todo o app.
- **Do** usar `shadow-sm` + `bg-card` branco pra separar conteúdo do
  fundo cinza da página — não introduzir borda pesada como alternativa.
- **Do** manter o valor numérico nunca recalculado no frontend — todo
  `StatTile`/card de dashboard exibe exatamente o que a API manda (ver
  PRODUCT.md, Capabilities and Constraints).

### Don't:
- **Don't** reproduzir o logo, wordmark "organizze" ou qualquer asset de
  marca do produto de referência — a inspiração é de topologia/linguagem
  visual, o nome e a marca são do fin-mec.
- **Don't** introduzir um segundo tom de verde pra outro propósito (ver
  The One Green Rule) — nem um terceiro acento de cor "de marca" (a
  paleta é deliberadamente restrita: verde + vermelho + neutros).
- **Don't** usar o acento de borda lateral (`border-l-4`) em mais de um
  stat tile por grid — ele marca "o líder", não é decoração genérica de
  card.
