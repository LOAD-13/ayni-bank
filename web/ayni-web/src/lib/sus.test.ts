import { describe, expect, it } from "vitest";

import { interpretarSus, puntuacionSus } from "./sus";

describe("Puntuación SUS", () => {
  it("todo neutral (3) da 50", () => {
    expect(puntuacionSus(Array(10).fill(3))).toBe(50);
  });

  it("la mejor respuesta posible da 100 y la peor 0: las pares puntúan al revés", () => {
    expect(puntuacionSus([5, 1, 5, 1, 5, 1, 5, 1, 5, 1])).toBe(100);
    expect(puntuacionSus([1, 5, 1, 5, 1, 5, 1, 5, 1, 5])).toBe(0);
  });

  it("rechaza respuestas incompletas o fuera de la escala", () => {
    expect(() => puntuacionSus([3, 3, 3])).toThrow(RangeError);
    expect(() => puntuacionSus([...Array(9).fill(3), 6])).toThrow(RangeError);
  });

  it("interpreta frente a la media de 68", () => {
    expect(interpretarSus(85)).toMatch(/Excelente/);
    expect(interpretarSus(70)).toMatch(/media de 68/);
    expect(interpretarSus(40)).toMatch(/Deficiente/);
  });
});
