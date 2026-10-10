import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRef } from "react";
import { Link, MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { useRouteFocus } from "./useRouteFocus";

function Shell() {
  const mainRef = useRef<HTMLElement>(null);
  useRouteFocus(mainRef);
  return (
    <>
      <nav>
        <Link to="/">Início</Link>
        <Link to="/contas">Contas</Link>
        <Link to="/contas?arquivadas=1">Arquivadas</Link>
      </nav>
      <main ref={mainRef}>
        <Outlet />
      </main>
    </>
  );
}

function renderApp() {
  render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route element={<Shell />}>
          <Route index element={<h1>Visão geral</h1>} />
          <Route path="contas" element={<h1>Contas</h1>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("useRouteFocus", () => {
  it("nomeia a aba pelo h1 sem tirar o foco na abertura do app", () => {
    renderApp();

    expect(document.title).toBe("Visão geral · fin-mec");
    expect(document.activeElement).toBe(document.body);
  });

  it("ao navegar, atualiza o título e leva o foco ao h1 da nova página", async () => {
    const user = userEvent.setup();
    renderApp();

    await user.click(screen.getByRole("link", { name: "Contas" }));

    const heading = screen.getByRole("heading", { level: 1, name: "Contas" });
    expect(document.title).toBe("Contas · fin-mec");
    expect(heading).toHaveFocus();
  });

  it("não move o foco quando só os filtros (search params) mudam", async () => {
    const user = userEvent.setup();
    renderApp();
    await user.click(screen.getByRole("link", { name: "Contas" }));

    const filter = screen.getByRole("link", { name: "Arquivadas" });
    await user.click(filter);

    expect(filter).toHaveFocus();
  });
});
