import { useEffect, useRef, type RefObject } from "react";
import { useLocation } from "react-router-dom";

const APP_NAME = "fin-mec";

/**
 * Numa SPA, trocar de página não recarrega nada: sem isto o título da aba nunca muda e o leitor
 * de tela não percebe que a página trocou (o foco fica no link clicado, no menu). O h1 de cada
 * página é a fonte do título, então nenhuma rota precisa declarar o próprio nome.
 *
 * Só reage ao pathname: mudar filtro/paginação (search params) não move o foco.
 */
export function useRouteFocus(mainRef: RefObject<HTMLElement | null>) {
  const { pathname } = useLocation();
  // Último pathname já tratado. Comparar com ele (e não com um "primeira execução") deixa o efeito
  // idempotente: o StrictMode roda o efeito duas vezes na montagem e não pode contar como navegação.
  const lastPathname = useRef<string | null>(null);

  useEffect(() => {
    const heading = mainRef.current?.querySelector("h1");
    const name = heading?.textContent?.trim();
    document.title = name ? `${name} · ${APP_NAME}` : APP_NAME;

    // Na abertura do app o foco fica onde o navegador puser; só a navegação move.
    const previous = lastPathname.current;
    lastPathname.current = pathname;
    if (previous === null || previous === pathname) return;
    const target = heading ?? mainRef.current;
    if (!target) return;
    target.setAttribute("tabindex", "-1");
    target.focus({ preventScroll: true });
  }, [pathname, mainRef]);
}
