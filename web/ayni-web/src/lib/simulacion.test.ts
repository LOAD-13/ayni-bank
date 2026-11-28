import { describe, expect, it } from "vitest";

import {
  cobrosConQr,
  cuotaMensual,
  factura,
  interesDeDeposito,
  proyeccionDeInversion,
  tasaDelPeriodo,
} from "@/lib/simulacion";

describe("simulación", () => {
  it("la tasa de 360 días es la TEA y la de un día coincide con el devengo del core", () => {
    expect(tasaDelPeriodo(4.5, 360)).toBeCloseTo(0.045, 12);
    // S/ 1 000 al 4.50 %: S/ 0.12227660 por día, el mismo número que la página de transparencia.
    expect(1000 * tasaDelPeriodo(4.5, 1)).toBeCloseTo(0.1222766, 7);
  });

  it("la cuota francesa devuelve el capital con sus intereses", () => {
    const { cuota, intereses, total } = cuotaMensual(10_000, 24.9, 24);
    expect(cuota).toBeGreaterThan(10_000 / 24);
    expect(total).toBeCloseTo(cuota * 24, 1);
    expect(intereses).toBeCloseTo(total - 10_000, 1);
    // Sin interés, la cuota es el capital dividido entre los meses.
    expect(cuotaMensual(1200, 0, 12).cuota).toBe(100);
  });

  it("depósito, inversión, factura y cobros con QR", () => {
    expect(interesDeDeposito(10_000, 5.8, 360)).toEqual({ interes: 580, total: 10_580 });
    const proyeccion = proyeccionDeInversion(1000, 100, 2, 0);
    expect(proyeccion).toEqual([
      { anio: 1, aportado: 2200, valor: 2200 },
      { anio: 2, aportado: 3400, valor: 3400 },
    ]);
    expect(factura(100)).toEqual({ valorDeVenta: 100, igv: 18, total: 118 });
    expect(cobrosConQr(5000, 1.5)).toEqual({ comision: 75, neto: 4925 });
  });
});
