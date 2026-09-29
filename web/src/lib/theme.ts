import { useCallback, useSyncExternalStore } from "react";

/**
 * Tema claro/escuro. "system" segue o sistema operacional e reage quando ele
 * muda. Aplicado como classe .dark no <html> (ver index.css). A preferência
 * fica no localStorage — é conveniência de tela, não dado de negócio.
 *
 * applyStoredTheme() roda em main.tsx antes do primeiro render: sem script
 * inline no index.html (a CSP do backend proíbe), é o mais cedo possível
 * para não piscar o tema errado.
 */
export type ThemePreference = "light" | "dark" | "system";

const STORAGE_KEY = "fin-mec:theme";
const media = () => window.matchMedia("(prefers-color-scheme: dark)");
const listeners = new Set<() => void>();

function readPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === "light" || stored === "dark" ? stored : "system";
  } catch {
    return "system";
  }
}

function resolve(preference: ThemePreference): "light" | "dark" {
  if (preference === "system") {
    return media().matches ? "dark" : "light";
  }
  return preference;
}

function apply(preference: ThemePreference) {
  document.documentElement.classList.toggle("dark", resolve(preference) === "dark");
}

export function applyStoredTheme() {
  apply(readPreference());
  media().addEventListener("change", () => {
    if (readPreference() === "system") {
      apply("system");
      listeners.forEach((l) => l());
    }
  });
}

export function setThemePreference(preference: ThemePreference) {
  try {
    localStorage.setItem(STORAGE_KEY, preference);
  } catch {
    // modo privado etc. — o tema só não persiste
  }
  apply(preference);
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useTheme() {
  const preference = useSyncExternalStore(subscribe, readPreference);
  const resolved = useSyncExternalStore(subscribe, () => resolve(readPreference()));
  const toggle = useCallback(
    () => setThemePreference(resolved === "dark" ? "light" : "dark"),
    [resolved],
  );
  return { preference, resolved, setPreference: setThemePreference, toggle };
}
