import { describe, expect, it } from "vitest";
import { nextQuery, safeNext } from "./safe-next";

describe("safeNext", () => {
  it("aceita só caminho interno", () => {
    expect(safeNext("/convite?token=abc")).toBe("/convite?token=abc");
    expect(safeNext(null)).toBe("/");
    expect(safeNext("https://evil.com")).toBe("/");
    expect(safeNext("//evil.com")).toBe("/");
    expect(safeNext("/\\evil.com")).toBe("/");
    expect(safeNext("javascript:alert(1)")).toBe("/");
  });

  it("repassa o destino codificado", () => {
    expect(nextQuery("/convite?token=a&b")).toBe("?next=%2Fconvite%3Ftoken%3Da%26b");
    expect(nextQuery(null)).toBe("");
  });
});
