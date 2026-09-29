/// <reference types="vitest/config" />
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";

// O backend Spring Boot roda em :8080 e serve toda a API sob /api (prefixo de
// path via WebConfig, nao mais context-path — ver o comentario daquela
// classe). Em dev o SPA roda em :5173 e o proxy encaminha /api para o
// backend, de modo que o browser enxerga tudo same-origin — cookie de sessao
// e CSRF funcionam sem CORS. O backend define BACKEND_PORT? nao: porta fixa
// 8080 por enquanto.
const BACKEND = process.env.VITE_BACKEND_ORIGIN ?? "http://localhost:8080";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      // "prompt": versão nova espera o usuário (toast "Atualizar") — recarregar sozinho poderia
      // jogar fora um lançamento sendo digitado.
      registerType: "prompt",
      // Registro feito em main.tsx: a CSP (script-src 'self') não permite o script inline.
      injectRegister: false,
      includeAssets: ["favicon.svg", "apple-touch-icon.png"],
      // .json e não .webmanifest: o Spring não conhece essa extensão e serviria o manifesto
      // como octet-stream (com nosniff, o navegador pode recusar).
      manifestFilename: "manifest.json",
      manifest: {
        id: "/",
        name: "fin-mec — finanças pessoais",
        short_name: "fin-mec",
        description: "Contas, cartões, orçamento e metas num lugar só.",
        lang: "pt-BR",
        start_url: "/",
        scope: "/",
        display: "standalone",
        theme_color: "#15803d",
        background_color: "#f4f6f9",
        icons: [
          { src: "/pwa-192.png", sizes: "192x192", type: "image/png" },
          { src: "/pwa-512.png", sizes: "512x512", type: "image/png" },
          {
            src: "/pwa-maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
        // Atalhos no ícone (pressionar e segurar no Android): o lançamento rápido direto.
        shortcuts: [
          { name: "Nova despesa", short_name: "Despesa", url: "/?novo=despesa" },
          { name: "Nova receita", short_name: "Receita", url: "/?novo=receita" },
          { name: "Lançamentos", url: "/lancamentos" },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
        navigateFallback: "/index.html",
        navigateFallbackDenylist: [/^\/api\//],
        cleanupOutdatedCaches: true,
        runtimeCaching: [
          {
            // Telas de leitura que continuam abrindo sem internet (e /auth/me, sem o qual o app
            // offline se acharia deslogado). Lista FECHADA de propósito:
            // login, CSRF, conta/segurança e comprovantes nunca vão para o cache do navegador — e
            // o que vai é apagado no logout (AuthProvider). A regex fica DENTRO da função: o
            // Workbox serializa esta função para o sw.js, onde constantes daqui não existem.
            urlPattern: ({ url, request }) =>
              request.method === "GET" &&
              (url.pathname === "/api/auth/me" ||
                /^\/api\/(dashboard|transactions|accounts|categories|insights|reports|budgets|goals|tags|bills|credit-cards|household|notifications)(\/|$)/.test(
                  url.pathname,
                )) &&
              !url.pathname.includes("/attachments") &&
              !url.pathname.endsWith("/export"),
            handler: "NetworkFirst",
            options: {
              cacheName: "fin-mec-api",
              networkTimeoutSeconds: 4,
              expiration: { maxEntries: 150, maxAgeSeconds: 7 * 24 * 60 * 60 },
              cacheableResponse: { statuses: [200] },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: BACKEND,
        changeOrigin: false,
      },
    },
  },
  build: {
    // Builda direto pra dentro do classpath do backend — o jar final do Spring
    // Boot serve isso como estatico (ver WebConfig#addResourceHandlers) e a
    // raiz do repo nunca guarda os dois "dist" (um do frontend, um do backend)
    // como artefatos separados; so o jar. emptyOutDir porque essa pasta e
    // sempre gerada, nunca editada a mao.
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
    // Bibliotecas em chunks próprios: mudam bem menos que o código do app, então o
    // navegador reaproveita o cache delas entre deploys.
    rollupOptions: {
      output: {
        manualChunks: {
          react: ["react", "react-dom", "react-router-dom"],
          charts: ["recharts"],
          ui: [
            "@radix-ui/react-dialog",
            "@radix-ui/react-popover",
            "@radix-ui/react-select",
            "cmdk",
            "lucide-react",
            "sonner",
          ],
          data: ["@tanstack/react-query", "react-hook-form", "@hookform/resolvers", "zod"],
        },
      },
    },
  },
  // A referencia de tipos no topo do arquivo e o que faz `test` abaixo tipar
  // certo (senao o defineConfig do vite puro nao reconhece a chave) — um so
  // arquivo de config em vez de vite.config.ts + vitest.config.ts separados.
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: true,
  },
});
