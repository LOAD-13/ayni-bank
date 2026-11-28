import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * HU-17 · AYNI-288: todo par de color de texto y de fondo que el código combina en un mismo elemento
 * cumple el contraste de WCAG 2.1 AA, calculado sobre los tokens de `tokens.css`: 4.5:1 en texto normal
 * y 3:1 en los títulos (texto grande). Lo que hereda el fondo de un antepasado lo revisa axe-core en las
 * pruebas de accesibilidad del navegador.
 */
const RAIZ = join(__dirname, "..");
const TOKENS = Object.fromEntries(
  [
    ...readFileSync(join(__dirname, "tokens.css"), "utf8").matchAll(
      /--([\w-]+):\s*(#[0-9a-fA-F]{6})/g,
    ),
  ].map(([, nombre, hex]) => [nombre, hex]),
);
// Clases de texto grande del sistema de diseño: títulos de 18.66 px en negrita o 24 px en adelante.
const TEXTO_GRANDE = /\btext-(h1|h2|h3|display)\b/;

function luminancia(hex: string) {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contraste(a: string, b: string) {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x);
  return (claro + 0.05) / (oscuro + 0.05);
}

function archivos(dir: string): string[] {
  return readdirSync(dir).flatMap((nombre) => {
    const ruta = join(dir, nombre);
    if (statSync(ruta).isDirectory()) return archivos(ruta);
    return /\.tsx$/.test(nombre) && !/\.test\.tsx$/.test(nombre) ? [ruta] : [];
  });
}

/** Pares texto/fondo escritos en un mismo className, sin variantes de estado (hover:, focus:...). */
function paresDelCodigo() {
  const pares: { archivo: string; texto: string; fondo: string; grande: boolean }[] = [];
  for (const archivo of archivos(RAIZ)) {
    for (const [, comillas, plantilla] of readFileSync(archivo, "utf8").matchAll(
      /className=(?:"([^"]+)"|\{`([^`]+)`\})/g,
    )) {
      const clases = comillas ?? plantilla;
      if (!clases) continue;
      const sueltas = clases.split(/\s+/).filter((c) => !c.includes(":"));
      const texto = sueltas.map((c) => /^text-([\w-]+)$/.exec(c)?.[1]).find((t) => t && TOKENS[t]);
      const fondo = sueltas.map((c) => /^bg-([\w-]+)$/.exec(c)?.[1]).find((f) => f && TOKENS[f]);
      if (texto && fondo) {
        pares.push({
          archivo: archivo.slice(RAIZ.length + 1),
          texto,
          fondo,
          grande: TEXTO_GRANDE.test(clases),
        });
      }
    }
  }
  return pares;
}

describe("Contraste de los pares de color (HU-17, WCAG 2.1 AA)", () => {
  it("calcula el contraste como WCAG", () => {
    expect(contraste("#000000", "#ffffff")).toBeCloseTo(21, 0);
    expect(contraste("#ffffff", "#ffffff")).toBeCloseTo(1, 5);
  });

  it("los tokens existen y el código combina al menos un par texto/fondo", () => {
    expect(Object.keys(TOKENS).length).toBeGreaterThan(20);
    expect(paresDelCodigo().length).toBeGreaterThan(0);
  });

  it("todo par texto/fondo del código cumple 4.5:1, o 3:1 en títulos", () => {
    const fallos = paresDelCodigo()
      .map((p) => ({ ...p, ratio: contraste(TOKENS[p.texto], TOKENS[p.fondo]) }))
      .filter((p) => p.ratio < (p.grande ? 3 : 4.5))
      .map((p) => `${p.archivo}: text-${p.texto} sobre bg-${p.fondo} = ${p.ratio.toFixed(2)}:1`);
    expect(fallos).toEqual([]);
  });
});
