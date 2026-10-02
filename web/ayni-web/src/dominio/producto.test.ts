import { describe, expect, it } from "vitest";

import {
  enPorcentaje,
  enSoles,
  interesAUnAno,
  proyectarAUnAno,
  TREA_SOLES,
} from "@/dominio/producto";

describe("Cifras del producto", () => {
  it("usa la convención peruana: punto decimal y coma de miles", () => {
    // El separador entre «S/» y la cifra es un espacio duro (U+00A0), que es lo que
    // emite Intl y lo que evita que el símbolo de moneda quede huérfano al final de
    // una línea. Se escribe explícito para que nadie lo «arregle» por un espacio
    // normal creyendo que es una errata.
    expect(enSoles(10450)).toBe("S/ 10,450.00");
    expect(enPorcentaje(4.5)).toBe("4.50 %");
  });

  it("proyecta a un año exactamente la TREA, porque la TREA ya es efectiva", () => {
    // La TREA incorpora la capitalización —por eso es *efectiva*—, así que un año al
    // 4.50 % da 4.50 %, ni un céntimo más. Componer sobre ella otra vez capitalizaría
    // dos veces y exageraría el rendimiento anunciado, que en una landing bancaria no
    // es un error de redondeo: es publicidad engañosa.
    expect(proyectarAUnAno(10000, TREA_SOLES)).toBeCloseTo(10450, 2);
    expect(interesAUnAno(10000, TREA_SOLES)).toBeCloseTo(450, 2);
  });

  it("el rendimiento es proporcional al capital", () => {
    expect(interesAUnAno(20000, TREA_SOLES)).toBeCloseTo(interesAUnAno(10000, TREA_SOLES) * 2, 2);
  });

  it("sin capital no hay rendimiento", () => {
    expect(interesAUnAno(0, TREA_SOLES)).toBe(0);
  });
});
