/// <reference types="vitest/config" />
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// O backend Spring Boot roda em :8080 e serve toda a API sob /api (prefixo de
// path via WebConfig, nao mais context-path — ver o comentario daquela
// classe). Em dev o SPA roda em :5173 e o proxy encaminha /api para o
// backend, de modo que o browser enxerga tudo same-origin — cookie de sessao
// e CSRF funcionam sem CORS. O backend define BACKEND_PORT? nao: porta fixa
// 8080 por enquanto.
const BACKEND = process.env.VITE_BACKEND_ORIGIN ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
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
