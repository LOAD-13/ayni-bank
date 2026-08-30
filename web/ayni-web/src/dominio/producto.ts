/* Cifras publicadas del producto.
 *
 * Viven aquí y no repartidas por los componentes porque son **afirmaciones sobre un
 * producto financiero**: la SBS exige que la TREA publicada sea la que efectivamente se
 * paga. Una cifra escrita a mano en una sección de la landing que no coincide con otra es
 * un problema de transparencia, no una errata.
 *
 * Origen: `docs/arquitectura/diseno-base.md` §2.3 y §3.7.1, y el catálogo
 * `tasa_producto` de la migración V1 de core-banking. Cuando el catálogo pase a leerse
 * por API, este fichero se sustituye por esa llamada. */

/** TREA de la cuenta de ahorro en soles. */
export const TREA_SOLES = 4.5;

/** TREA de la cuenta de ahorro en dólares. */
export const TREA_DOLARES = 1.2;

/** Comisión de mantenimiento mensual. Cero, y ese es el argumento del producto. */
export const MANTENIMIENTO_MENSUAL = 0;

/** Spread cambiario estándar, aplicado simétricamente (±0.25 %). */
export const SPREAD_ESTANDAR = 0.5;

/** Spread para clientes con saldo promedio mensual ≥ S/ 5 000. */
export const SPREAD_PREFERENCIAL = 0.25;

/** Saldo promedio mensual desde el que aplica la tarifa preferencial. */
export const SALDO_PREFERENCIAL = 5000;

const FORMATO_SOLES = new Intl.NumberFormat("es-PE", {
  style: "currency",
  currency: "PEN",
  minimumFractionDigits: 2,
});

const FORMATO_PORCENTAJE = new Intl.NumberFormat("es-PE", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function enSoles(importe: number): string {
  return FORMATO_SOLES.format(importe);
}

export function enPorcentaje(valor: number): string {
  return `${FORMATO_PORCENTAJE.format(valor)} %`;
}

/**
 * Saldo proyectado a un año con **devengo diario**, que es como funciona el producto:
 * el interés se calcula todos los días sobre el saldo y se abona a fin de mes.
 *
 * La TREA ya incorpora la capitalización —por eso es *efectiva*— así que proyectar con
 * ella directamente sería capitalizar dos veces. Se convierte primero a tasa diaria
 * equivalente y se compone sobre los 365 días.
 */
export function proyectarAUnAno(capital: number, treaPorcentaje: number): number {
  const trea = treaPorcentaje / 100;
  const tasaDiaria = Math.pow(1 + trea, 1 / 365) - 1;
  return capital * Math.pow(1 + tasaDiaria, 365);
}

/** Interés ganado en un año sobre el capital indicado. */
export function interesAUnAno(capital: number, treaPorcentaje: number): number {
  return proyectarAUnAno(capital, treaPorcentaje) - capital;
}
