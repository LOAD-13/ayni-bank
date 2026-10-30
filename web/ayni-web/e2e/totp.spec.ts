import { expect, test } from "@playwright/test";

import { codigoTotp } from "./apoyo/totp";

/** Vectores de prueba del RFC 6238 (apéndice B, SHA-1): si el generador se desvía, aquí se ve. */
test.describe("Generador TOTP de la suite contra la nube", () => {
  // «12345678901234567890» en Base32.
  const SEMILLA = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  for (const [segundos, esperado] of [
    [59, "94287082"],
    [1111111109, "07081804"],
    [1234567890, "89005924"],
    [20000000000, "65353130"],
  ] as const) {
    test(`a los ${segundos} s da ${esperado}`, () => {
      expect(codigoTotp(SEMILLA, segundos * 1000, 8)).toBe(esperado);
    });
  }

  test("una semilla que no es Base32 se rechaza", () => {
    expect(() => codigoTotp("no-es-base32!")).toThrow(/Base32/);
  });
});
