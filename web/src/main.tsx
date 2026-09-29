import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import { App } from "./App";
import { applyStoredTheme } from "./lib/theme";
import { startPwa } from "./app/pwa-register";

applyStoredTheme();
startPwa();

const root = document.getElementById("root");
if (!root) {
  throw new Error("Elemento #root nao encontrado em index.html");
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
