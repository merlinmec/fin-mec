import { useCallback, useSyncExternalStore } from "react";

const STORAGE_KEY = "fin-mec:hide-balances";
const MASK = "••••••";
const listeners = new Set<() => void>();

function read(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

let current = read();

function setHidden(next: boolean) {
  current = next;
  try {
    localStorage.setItem(STORAGE_KEY, next ? "1" : "0");
  } catch {
    // localStorage indisponível (modo privado, etc.) — preferência só não persiste.
  }
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Preferência global de "ocultar saldo" (padrão visual do Organizze: um
 * ícone de olho ao lado do saldo). Store único do app — o olho do dashboard,
 * a paleta de comandos e o menu do usuário mexem na mesma preferência, e
 * toda tela que mostra saldo reage junto. Persistida em localStorage: é
 * conveniência de tela, não estado de negócio.
 */
export function useBalanceVisibility() {
  const hidden = useSyncExternalStore(subscribe, () => current);
  const toggle = useCallback(() => setHidden(!current), []);
  const mask = useCallback((formatted: string) => (hidden ? MASK : formatted), [hidden]);
  return { hidden, toggle, mask };
}
