# fin-mec — web

Frontend SPA do fin-mec. React + TypeScript + Vite, Tailwind v4 + shadcn/ui,
TanStack Query, React Router. Cliente HTTP escrito à mão sobre `fetch`.

## Rodar em dev

O backend (Spring Boot) precisa estar no ar em `http://localhost:8080` — ele
serve tudo sob `/api` (context-path). O dev server do Vite faz proxy de `/api`
para lá, então o browser vê tudo same-origin (cookie de sessão + CSRF sem CORS).

```bash
npm install
npm run dev        # http://localhost:5173
```

Origem do backend configurável por `VITE_BACKEND_ORIGIN` (default
`http://localhost:8080`).

## Scripts

| Script | O quê |
| --- | --- |
| `npm run dev` | dev server com HMR |
| `npm run build` | typecheck (`tsc -b`) + build de produção em `dist/` |
| `npm run lint` | ESLint |
| `npm run typecheck` | só o typecheck, sem emitir |
| `npm run format` | Prettier nos fontes |

## Estrutura

```
src/
  api/        cliente HTTP (client.ts), bootstrap de CSRF, funções por recurso
  auth/       AuthProvider + contexto de sessão (boot: /api/csrf + /api/auth/me)
  app/        queryClient, router, layout (AppShell, ProtectedRoute)
  components/ui/  primitivos shadcn/ui (adicionar via `npx shadcn@latest add ...`)
  routes/     telas (placeholders na FE-0)
  lib/        utilitários (cn)
```

## Contrato da API

Sem geração de código. Os tipos de DTO são escritos à mão ao lado de cada módulo
em `src/api/`. Referência de contrato: `http://localhost:8080/api/swagger-ui.html`
(atrás de autenticação) ou `GET /api/v3/api-docs`.
