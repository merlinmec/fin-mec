import { describe, expect, it } from "vitest";
import { daysUntil, formatDate, formatYearMonth, shiftYearMonth, yearMonthOf } from "./dates";

describe("formatDate", () => {
  it("converte yyyy-MM-dd pra dd/mm/aaaa", () => {
    expect(formatDate("2026-09-11")).toBe("11/09/2026");
  });
});

describe("formatYearMonth", () => {
  it("converte yyyy-MM pro nome do mes por extenso, com a primeira letra maiuscula", () => {
    expect(formatYearMonth("2026-09")).toBe("Setembro de 2026");
  });
});

describe("shiftYearMonth", () => {
  it("avanca meses", () => {
    expect(shiftYearMonth("2026-09", 1)).toBe("2026-10");
  });

  it("volta meses", () => {
    expect(shiftYearMonth("2026-09", -1)).toBe("2026-08");
  });

  it("vira o ano corretamente", () => {
    expect(shiftYearMonth("2026-12", 1)).toBe("2027-01");
    expect(shiftYearMonth("2026-01", -1)).toBe("2025-12");
  });
});

describe("yearMonthOf", () => {
  it("deriva a competencia a partir da data", () => {
    expect(yearMonthOf("2026-09-11")).toBe("2026-09");
  });
});

describe("daysUntil", () => {
  it("e positivo pra datas futuras e negativo pra datas passadas", () => {
    const today = new Date();
    const future = new Date(today);
    future.setDate(future.getDate() + 5);
    const past = new Date(today);
    past.setDate(past.getDate() - 5);

    const toIso = (d: Date) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

    expect(daysUntil(toIso(future))).toBe(5);
    expect(daysUntil(toIso(past))).toBe(-5);
    expect(daysUntil(toIso(today))).toBe(0);
  });
});
