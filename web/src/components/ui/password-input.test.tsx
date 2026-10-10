import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Label } from "./label";
import { PasswordInput } from "./password-input";

function renderField() {
  render(
    <>
      <Label htmlFor="senha">Senha</Label>
      <PasswordInput id="senha" defaultValue="segredo123" />
    </>,
  );
  return screen.getByLabelText("Senha");
}

describe("PasswordInput", () => {
  it("começa oculto e alterna entre mostrar e ocultar", async () => {
    const user = userEvent.setup();
    const field = renderField();
    expect(field).toHaveAttribute("type", "password");

    await user.click(screen.getByRole("button", { name: "Mostrar senha" }));
    expect(field).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Ocultar senha" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    await user.click(screen.getByRole("button", { name: "Ocultar senha" }));
    expect(field).toHaveAttribute("type", "password");
  });

  it("o botão não envia o formulário e funciona pelo teclado", async () => {
    const user = userEvent.setup();
    let submitted = false;
    render(
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submitted = true;
        }}
      >
        <PasswordInput aria-label="Senha" defaultValue="x" />
      </form>,
    );

    await user.tab();
    await user.tab();
    expect(screen.getByRole("button", { name: "Mostrar senha" })).toHaveFocus();
    await user.keyboard("{Enter}");

    expect(screen.getByLabelText("Senha")).toHaveAttribute("type", "text");
    expect(submitted).toBe(false);
  });
});
