import { describe, expect, it } from "vitest";
import { formatMoney } from "./money";

describe("formatMoney", () => {
  it("formata em real com separador de milhar e duas casas decimais", () => {
    const result = formatMoney(1500.5);
    expect(result).toContain("R$");
    expect(result).toContain("1.500,50");
  });

  it("formata zero com duas casas decimais", () => {
    expect(formatMoney(0)).toContain("0,00");
  });

  it("mantem o sinal em valores negativos", () => {
    const result = formatMoney(-42);
    expect(result).toContain("-");
    expect(result).toContain("42,00");
  });
});
