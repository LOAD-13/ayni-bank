import { describe, expect, it } from "vitest";

import type { Movimiento } from "@/lib/api";
import {
  categoriaDe,
  claveDeDia,
  conceptoLegible,
  gastosPorCategoria,
  nombreDeDia,
  resumirPorMes,
  saldosAlCierre,
  ultimosMeses,
} from "@/lib/movimientos";

const mov = (tipo: "ABONO" | "CARGO", importe: string, concepto: string, registradoEn: string) =>
  ({ movimientoId: concepto, tipo, importe, moneda: "PEN", concepto, registradoEn }) as Movimiento;

describe("movimientos", () => {
  it("lee la categoría del concepto que escribe el banco", () => {
    expect(categoriaDe("Deposito simulado")).toBe("Depósito");
    expect(categoriaDe("Intereses ganados · 2026-10")).toBe("Intereses");
    expect(categoriaDe("Transferencia interbancaria")).toBe("A otro banco");
    expect(categoriaDe("Cambio PEN a USD")).toBe("Cambio de moneda");
    expect(categoriaDe("REVELAR-PAN-4821")).toBe("Tarjeta");
    expect(categoriaDe("Cena · a **********0031")).toBe("Transferencia");
    expect(conceptoLegible("REVELAR-PAN-4821")).toBe("Consulta del número de tu tarjeta");
    expect(conceptoLegible("Cena · a **********0031")).toBe("Cena · a •••• 0031");
  });

  it("agrupa por el día y el mes de Lima, no por el de UTC", () => {
    // 03:00 UTC del 1-dic son las 22:00 del 30-nov en Lima.
    expect(claveDeDia("2026-12-01T03:00:00Z")).toBe("2026-11-30");
    expect(ultimosMeses(new Date("2026-02-10T15:00:00Z"), 4)).toEqual([
      "2025-11",
      "2025-12",
      "2026-01",
      "2026-02",
    ]);
    const hoy = new Date("2026-11-19T15:00:00Z");
    expect(nombreDeDia("2026-11-19", hoy)).toBe("Hoy");
    expect(nombreDeDia("2026-11-18", hoy)).toBe("Ayer");
    expect(nombreDeDia("2026-11-11", hoy)).toMatch(/^Miércoles 11 de noviembre$/);
  });

  it("suma entradas, salidas y gastos en céntimos exactos", () => {
    const lista = [
      mov("ABONO", "0.10", "Deposito simulado", "2026-11-02T15:00:00Z"),
      mov("ABONO", "0.20", "Deposito simulado", "2026-11-03T15:00:00Z"),
      mov("CARGO", "150.50", "Cena · a ****0031", "2026-11-04T15:00:00Z"),
      mov("CARGO", "60", "Transferencia interbancaria", "2026-11-05T15:00:00Z"),
      mov("CARGO", "9.99", "Fuera del rango", "2026-05-05T15:00:00Z"),
    ];
    const [octubre, noviembre] = resumirPorMes(lista, ["2026-10", "2026-11"]);
    expect(octubre).toEqual({ clave: "2026-10", entradas: BigInt(0), salidas: BigInt(0) });
    // 0.10 + 0.20 en coma flotante daría 0.30000000000000004.
    expect(noviembre.entradas).toBe(BigInt(30));
    expect(noviembre.salidas).toBe(BigInt(21050));
    const conSaldo = [
      { ...lista[0], saldoPosterior: "100.10" },
      { ...lista[2], saldoPosterior: "50.00" },
    ];
    expect(saldosAlCierre(conSaldo, ["2026-10", "2026-11", "2026-12"])).toEqual([
      null,
      "50.00",
      "50.00",
    ]);
    expect(gastosPorCategoria(lista.slice(0, 4))).toEqual([
      { categoria: "Transferencia", centimos: BigInt(15050) },
      { categoria: "A otro banco", centimos: BigInt(6000) },
    ]);
  });
});
