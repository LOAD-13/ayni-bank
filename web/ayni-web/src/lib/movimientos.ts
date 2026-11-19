/* Lo que la banca deriva de la lista de movimientos: categoría, mes, día y totales.
 *
 * Los totales se suman en céntimos con `bigint`, como el resto de importes de la web: las
 * gráficas son un resumen, pero las cifras que se leen junto a ellas tienen que cuadrar. */

import type { Movimiento } from "@/lib/api";
import { centimosDe } from "@/lib/formato";

export type Categoria =
  "Depósito" | "Intereses" | "A otro banco" | "Cambio de moneda" | "Tarjeta" | "Transferencia";

/** La categoría se lee del concepto que escribe el banco (ver `Movimiento.java`). */
export function categoriaDe(concepto: string): Categoria {
  if (/^REVELAR-PAN-|tarjeta/i.test(concepto)) return "Tarjeta";
  if (/dep[oó]sito/i.test(concepto)) return "Depósito";
  if (/^intereses/i.test(concepto)) return "Intereses";
  if (/interbancaria/i.test(concepto)) return "A otro banco";
  if (/^cambio /i.test(concepto)) return "Cambio de moneda";
  return "Transferencia";
}

/** «**********0031» → «•••• 0031»: los asteriscos del enmascarado son ruido al leer. */
export function cuentaLegible(texto: string): string {
  return texto.replace(/\*{4,}(\d{4})/g, "•••• $1");
}

/** El concepto como lo leería una persona: sin códigos internos y con sus tildes. */
export function conceptoLegible(concepto: string): string {
  if (/^REVELAR-PAN-/.test(concepto)) return "Consulta del número de tu tarjeta";
  return cuentaLegible(
    concepto.replace(/^Deposito/, "Depósito").replace(/^Devolucion/, "Devolución"),
  );
}

const CERO = BigInt(0);
const LIMA = "America/Lima";
const FECHA_ISO = new Intl.DateTimeFormat("en-CA", {
  timeZone: LIMA,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** «2026-11-18», el día en Lima aunque el servidor registre en UTC. */
export function claveDeDia(fecha: string | Date): string {
  return FECHA_ISO.format(new Date(fecha));
}

/** «2026-11». */
export function claveDeMes(fecha: string | Date): string {
  return claveDeDia(fecha).slice(0, 7);
}

/** Las claves de los `n` últimos meses, del más antiguo al actual. */
export function ultimosMeses(hoy: Date, n: number): string[] {
  const [anio, mes] = claveDeMes(hoy).split("-").map(Number);
  return Array.from({ length: n }, (_, i) => {
    const total = anio * 12 + (mes - 1) - (n - 1 - i);
    return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, "0")}`;
  });
}

const NOMBRE_DE_MES = new Intl.DateTimeFormat("es-PE", { month: "long", timeZone: "UTC" });

/** «2026-11» → «noviembre». */
export function nombreDeMes(clave: string): string {
  return NOMBRE_DE_MES.format(new Date(`${clave}-15T12:00:00Z`));
}

const NOMBRE_DE_DIA = new Intl.DateTimeFormat("es-PE", {
  weekday: "long",
  day: "numeric",
  month: "long",
  timeZone: "UTC",
});

/** «Hoy», «Ayer» o «Martes 11 de noviembre». */
export function nombreDeDia(clave: string, hoy: Date): string {
  if (clave === claveDeDia(hoy)) return "Hoy";
  if (clave === claveDeDia(new Date(hoy.getTime() - 86_400_000))) return "Ayer";
  const texto = NOMBRE_DE_DIA.format(new Date(`${clave}T12:00:00Z`)).replace(",", "");
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

export interface ResumenDelMes {
  clave: string;
  entradas: bigint;
  salidas: bigint;
}

/** Entradas y salidas de cada mes pedido, en céntimos; un mes sin movimientos suma cero. */
export function resumirPorMes(movimientos: Movimiento[], claves: string[]): ResumenDelMes[] {
  const meses = new Map(claves.map((clave) => [clave, { clave, entradas: CERO, salidas: CERO }]));
  for (const m of movimientos) {
    const mes = meses.get(claveDeMes(m.registradoEn));
    if (!mes) continue;
    if (m.tipo === "ABONO") mes.entradas += centimosDe(m.importe);
    else mes.salidas += centimosDe(m.importe);
  }
  return [...meses.values()];
}

/** En qué se fue el dinero: los cargos sumados por categoría, de mayor a menor. */
export function gastosPorCategoria(
  movimientos: Movimiento[],
): { categoria: Categoria; centimos: bigint }[] {
  const suma = new Map<Categoria, bigint>();
  for (const m of movimientos) {
    if (m.tipo !== "CARGO") continue;
    const categoria = categoriaDe(m.concepto);
    suma.set(categoria, (suma.get(categoria) ?? CERO) + centimosDe(m.importe));
  }
  return [...suma]
    .map(([categoria, centimos]) => ({ categoria, centimos }))
    .sort((a, b) => (b.centimos > a.centimos ? 1 : b.centimos < a.centimos ? -1 : 0));
}
