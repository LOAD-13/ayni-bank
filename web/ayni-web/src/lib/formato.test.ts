import { describe, expect, it } from "vitest";

import {
  dividirHalfEven,
  porcentajeUsado,
  proyeccionAUnAno,
  rendimientoMensualEstimado,
} from "./formato";

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

describe("proyeccionAUnAno · T-34", () => {
  it("aplica la TREA una sola vez, porque ya es efectiva", () => {
    expect(proyeccionAUnAno("10000", "4.50")).toBe("10450.00");
    expect(proyeccionAUnAno("10000", "1.2")).toBe("10120.00");
  });

  it("es exacta donde la coma flotante se desvía", () => {
    // En double, 0.1 × (1 + 4.35 / 100) da 0.10435000000000001.
    expect(proyeccionAUnAno("0.10", "4.35")).toBe("0.10");
    expect(proyeccionAUnAno("1000.00", "4.355")).toBe("1043.55");
  });

  it("redondea con HALF_EVEN la fracción de céntimo", () => {
    // 1 × 1.0050 = 1.005 → 1.00 (par) · 3 × 1.0050 = 3.015 → 3.02 (par).
    // Con parseFloat, 3 × 1.005 daba 3.0149999999999997 y se mostraba 3.01.
    expect(proyeccionAUnAno("1.00", "0.50")).toBe("1.00");
    expect(proyeccionAUnAno("3.00", "0.50")).toBe("3.02");
  });

  it("con TREA cero devuelve el mismo capital", () => {
    expect(proyeccionAUnAno("10000", "0")).toBe("10000.00");
  });

  it("devuelve null si la TREA o el capital no son decimales", () => {
    expect(proyeccionAUnAno("10000", "")).toBeNull();
    expect(proyeccionAUnAno("10000", "4.5%")).toBeNull();
    expect(proyeccionAUnAno("diez mil", "4.50")).toBeNull();
  });
});

describe("porcentajeUsado · HU-10", () => {
  it("calcula la parte usada del límite, en entero", () => {
    expect(porcentajeUsado("250.00", "300.00")).toBe(83);
    expect(porcentajeUsado("150", "300.00")).toBe(50);
    expect(porcentajeUsado("0.00", "300.00")).toBe(0);
  });

  it("nunca pasa de 100 ni baja de 0", () => {
    expect(porcentajeUsado("450.00", "300.00")).toBe(100);
    expect(porcentajeUsado("-10.00", "300.00")).toBe(0);
  });

  it("un límite cero o un texto que no es importe dan 0", () => {
    expect(porcentajeUsado("10.00", "0.00")).toBe(0);
    expect(porcentajeUsado("diez", "300.00")).toBe(0);
  });
});
