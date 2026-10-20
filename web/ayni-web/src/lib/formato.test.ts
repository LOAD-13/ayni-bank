import { describe, expect, it } from "vitest";

import { dividirHalfEven, rendimientoMensualEstimado } from "./formato";

describe("dividirHalfEven", () => {
  it("redondea al entero más cercano cuando no hay empate", () => {
    expect(dividirHalfEven(BigInt(26), BigInt(10))).toBe(BigInt(3));
    expect(dividirHalfEven(BigInt(24), BigInt(10))).toBe(BigInt(2));
  });

  it("en la mitad exacta va al par, no siempre hacia arriba", () => {
    expect(dividirHalfEven(BigInt(25), BigInt(10))).toBe(BigInt(2));
    expect(dividirHalfEven(BigInt(35), BigInt(10))).toBe(BigInt(4));
  });

  it("con negativos es simétrico, como BigDecimal", () => {
    expect(dividirHalfEven(BigInt(-25), BigInt(10))).toBe(BigInt(-2));
    expect(dividirHalfEven(BigInt(-35), BigInt(10))).toBe(BigInt(-4));
    expect(dividirHalfEven(BigInt(-26), BigInt(10))).toBe(BigInt(-3));
  });

  it("rechaza un divisor cero o negativo", () => {
    expect(() => dividirHalfEven(BigInt(1), BigInt(0))).toThrow(RangeError);
    expect(() => dividirHalfEven(BigInt(1), BigInt(-2))).toThrow(RangeError);
  });
});

describe("rendimientoMensualEstimado · T-33", () => {
  it("redondea con HALF_EVEN en lugar de truncar", () => {
    // 850 × 4.50 % / 12 = 3.1875. Truncar daba 3.18: un céntimo menos al cliente.
    expect(rendimientoMensualEstimado("850.00", "4.50")).toBe("3.19");
    expect(rendimientoMensualEstimado("1000.00", "4.00")).toBe("3.33");
  });

  it("en el medio céntimo exacto va al céntimo par", () => {
    // 18 × 1 % / 12 = 0.015 → 0.02 (sube al par) · 30 × 1 % / 12 = 0.025 → 0.02 (se queda)
    expect(rendimientoMensualEstimado("18.00", "1.00")).toBe("0.02");
    expect(rendimientoMensualEstimado("30.00", "1.00")).toBe("0.02");
  });

  it("usa todos los decimales de la TREA, no solo dos", () => {
    // Con la TREA cortada a 4.55 saldría 37.92.
    expect(rendimientoMensualEstimado("10000.00", "4.555")).toBe("37.96");
  });

  it("sin saldo no hay rendimiento", () => {
    expect(rendimientoMensualEstimado("0.00", "4.50")).toBe("0.00");
  });

  it("rechaza un importe o una tasa que no son decimales", () => {
    expect(() => rendimientoMensualEstimado("abc", "4.50")).toThrow(RangeError);
    expect(() => rendimientoMensualEstimado("850.00", "4,50")).toThrow(RangeError);
  });
});
