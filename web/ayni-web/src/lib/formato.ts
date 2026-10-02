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

/** Clave de idempotencia nueva: una por intento de operación, no por clic. */
export function nuevaClave(): string {
  return crypto.randomUUID();
}
