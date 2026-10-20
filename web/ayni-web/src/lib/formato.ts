/* Formato de importes y fechas para pantalla.
 *
 * Los importes llegan del servidor como texto («12480.65») y se formatean como texto, sin
 * pasar nunca por `Number`: así no hay coma flotante entre la base de datos y lo que ve el
 * cliente. */

const SIMBOLOS: Record<string, string> = { PEN: "S/", USD: "US$" };

/** «12480.65» → «S/ 12 480.65». Agrupa con espacio fino, como el diseño aprobado. */
export function formatearImporte(texto: string, moneda = "PEN"): string {
  const negativo = texto.startsWith("-");
  const limpio = negativo ? texto.slice(1) : texto;
  const [entero = "0", decimales = ""] = limpio.split(".");
  const agrupado = entero.replace(/\B(?=(\d{3})+(?!\d))/g, " ");
  const centimos = (decimales + "00").slice(0, 2);
  return `${negativo ? "−" : ""}${SIMBOLOS[moneda] ?? moneda} ${agrupado}.${centimos}`;
}

/** Fecha y hora en la zona de Lima, que es la del cliente aunque el servidor esté en UTC. */
export function formatearFecha(iso: string): string {
  return new Intl.DateTimeFormat("es-PE", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "America/Lima",
  }).format(new Date(iso));
}

/**
 * Normaliza lo que el usuario escribe en un campo de importe: «1 250,5» → «1250.50».
 * Devuelve `null` si no es un importe válido con como mucho dos decimales.
 */
export function normalizarImporte(escrito: string): string | null {
  const limpio = escrito.replace(/[\s ]/g, "").replace(",", ".");
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(limpio)) return null;
  const [entero, decimales = ""] = limpio.split(".");
  return `${String(Number(entero))}.${(decimales + "00").slice(0, 2)}`;
}

/** Un decimal exacto: «4.5» es `{ unidades: 45n, escala: 1 }`, es decir 45 × 10⁻¹. */
interface Decimal {
  unidades: bigint;
  escala: number;
}

/** Lee un decimal escrito como texto («-12.345») sin pasar por `Number`. `null` si no lo es. */
function leerDecimal(texto: string): Decimal | null {
  if (!/^-?\d+(\.\d+)?$/.test(texto)) return null;
  const [entero, decimales = ""] = texto.split(".");
  return { unidades: BigInt(entero + decimales), escala: decimales.length };
}

/** 10 elevado a `n`, en `bigint`. El `target` ES2017 no admite `**` con `bigint`. */
function potenciaDeDiez(n: number): bigint {
  let resultado = BigInt(1);
  for (let i = 0; i < n; i++) resultado *= BigInt(10);
  return resultado;
}

/**
 * Divide redondeando **HALF_EVEN**, como `RoundingMode.HALF_EVEN` del backend: la mitad
 * exacta va al par más cercano (0.125 → 0.12, 0.135 → 0.14). La división de `bigint`
 * trunca, y truncar siempre hacia abajo le quita céntimos al cliente.
 */
export function dividirHalfEven(dividendo: bigint, divisor: bigint): bigint {
  const cero = BigInt(0);
  if (divisor <= cero) throw new RangeError("El divisor debe ser positivo");
  const negativo = dividendo < cero;
  const absoluto = negativo ? -dividendo : dividendo;
  let cociente = absoluto / divisor;
  const dobleDelResto = (absoluto % divisor) * BigInt(2);
  if (
    dobleDelResto > divisor ||
    (dobleDelResto === divisor && cociente % BigInt(2) === BigInt(1))
  ) {
    cociente += BigInt(1);
  }
  return negativo ? -cociente : cociente;
}

/** 1250n → «12.50». */
function deCentimos(centimos: bigint): string {
  const cien = BigInt(100);
  const negativo = centimos < BigInt(0);
  const absoluto = negativo ? -centimos : centimos;
  const decimales = String(absoluto % cien).padStart(2, "0");
  return `${negativo ? "-" : ""}${absoluto / cien}.${decimales}`;
}

/**
 * Interés aproximado de un mes con la TREA vigente: saldo × TREA / 12, redondeado a
 * céntimos con HALF_EVEN y sin coma flotante. Usa todos los decimales de la TREA, no solo
 * dos. Es una estimación para el panel, no el devengo real.
 */
export function rendimientoMensualEstimado(saldo: string, trea: string): string {
  const s = leerDecimal(saldo);
  const t = leerDecimal(trea);
  if (!s || !t) throw new RangeError(`Importe o tasa no válidos: «${saldo}», «${trea}»`);
  // saldo × (trea / 100) / 12, expresado en céntimos (× 100): saldo × trea / 12.
  const divisor = potenciaDeDiez(s.escala + t.escala) * BigInt(12);
  return deCentimos(dividirHalfEven(s.unidades * t.unidades, divisor));
}

/** Clave de idempotencia nueva: una por intento de operación, no por clic. */
export function nuevaClave(): string {
  return crypto.randomUUID();
}
