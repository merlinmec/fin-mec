import { useCallback, useEffect, useState } from "react";

const STORAGE_KEY = "fin-mec:hide-balances";
const MASK = "••••••";

/**
 * Preferência global de "ocultar saldo" (padrão visual do Organizze: um
 * ícone de olho ao lado do saldo). Persistida em localStorage — é só uma
 * conveniência de tela, não estado de negócio, então fica só no navegador
 * mesmo (sem sincronizar com o backend).
 */
export function useBalanceVisibility() {
  const [hidden, setHidden] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === "1";
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, hidden ? "1" : "0");
    } catch {
      // localStorage indisponível (modo privado, etc.) — preferência só não persiste.
    }
  }, [hidden]);

  const toggle = useCallback(() => setHidden((v) => !v), []);

  const mask = useCallback((formatted: string) => (hidden ? MASK : formatted), [hidden]);

  return { hidden, toggle, mask };
}
