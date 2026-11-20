import { ChevronDown } from "lucide-react";

import type { Movimiento } from "@/lib/api";
import { deCentimos, formatearImporte } from "@/lib/formato";
import {
  claveDeMes,
  gastosPorCategoria,
  nombreDeMes,
  resumirPorMes,
  saldosAlCierre,
} from "@/lib/movimientos";

const soles = (centimos: bigint) => formatearImporte(deCentimos(centimos), "PEN");
const CERO = BigInt(0);

/** Altura de una barra en % del máximo. Solo es visual: las cifras se leen en texto. */
function altura(valor: bigint, maximo: bigint): number {
  if (maximo === CERO || valor === CERO) return 0;
  return Math.max(3, Number((valor * BigInt(1000)) / maximo) / 10);
}

/**
 * Lo que entró y salió cada mes, con el mes elegido explicado en una frase, y en qué se fue
 * el dinero ese mes. Cada mes es un botón: elegirlo filtra también el historial.
 */
export function ResumenDeMeses({
  movimientos,
  meses,
  elegido,
  elegir,
}: {
  movimientos: Movimiento[];
  meses: string[];
  elegido: string;
  elegir: (clave: string) => void;
}) {
  const resumen = resumirPorMes(movimientos, meses);
  const maximo = resumen.reduce(
    (max, m) => [m.entradas, m.salidas, max].reduce((a, b) => (b > a ? b : a)),
    CERO,
  );
  const delMes = resumen.find((m) => m.clave === elegido) ?? resumen[resumen.length - 1];
  const neto = delMes.entradas - delMes.salidas;
  const gastos = gastosPorCategoria(
    movimientos.filter((m) => claveDeMes(m.registradoEn) === delMes.clave),
  );
  const nombre = nombreDeMes(delMes.clave);
  // El saldo al cierre de cada mes, en dorado sobre las barras: la gráfica del ahorro.
  const saldos = saldosAlCierre(movimientos, meses);
  const saldoMaximo = Math.max(1, ...saldos.map((v) => Number(v ?? 0)));
  const puntos = saldos
    .map((v, i) => (v === null ? null : `${i * 100 + 50},${100 - (Number(v) / saldoMaximo) * 88}`))
    .filter(Boolean)
    .join(" ");

  return (
    <section
      aria-labelledby="titulo-resumen"
      className="grid grid-cols-1 gap-8 rounded-[20px] border border-azul-200 bg-blanco p-6 sm:p-7 lg:grid-cols-[minmax(0,1fr)_300px]"
    >
      <div className="min-w-0">
        <h2 id="titulo-resumen" className="text-h3 font-bold text-azul-900 first-letter:uppercase">
          {nombre} en tu cuenta
        </h2>
        <p aria-live="polite" className="mt-1 max-w-[60ch] text-body text-gris-700">
          {delMes.entradas === CERO && delMes.salidas === CERO ? (
            `En ${nombre} no hubo movimientos.`
          ) : (
            <>
              Entraron <strong className="cifra text-gris-900">{soles(delMes.entradas)}</strong> y
              salieron <strong className="cifra text-gris-900">{soles(delMes.salidas)}</strong>.{" "}
              {neto >= CERO ? (
                <>
                  Ahorraste <strong className="cifra text-exito">{soles(neto)}</strong>.
                </>
              ) : (
                <>
                  Salió <strong className="cifra text-gris-900">{soles(-neto)}</strong> más de lo
                  que entró.
                </>
              )}
            </>
          )}
        </p>

        <div className="mt-6 flex items-center gap-5 text-small text-gris-700" aria-hidden="true">
          <span className="flex items-center gap-2">
            <span className="h-3 w-3 rounded-sm bg-azul-700" /> Entró
          </span>
          <span className="flex items-center gap-2">
            <span className="h-3 w-3 rounded-sm bg-azul-300" /> Salió
          </span>
          {puntos && (
            <span className="flex items-center gap-2">
              <span className="h-0.5 w-4 rounded bg-dorado-600" /> Saldo al cierre
            </span>
          )}
        </div>

        <div
          role="group"
          aria-label="Elige un mes"
          className="relative mt-3 grid h-28 grid-cols-6 items-end gap-1 border-b border-azul-200 sm:h-48 sm:gap-3"
        >
          {puntos && (
            <svg
              aria-hidden="true"
              viewBox="0 0 600 100"
              preserveAspectRatio="none"
              className="pointer-events-none absolute inset-0 h-full w-full"
            >
              <polyline
                points={puntos}
                fill="none"
                className="stroke-dorado-600"
                strokeWidth="2.5"
                strokeLinejoin="round"
                vectorEffect="non-scaling-stroke"
              />
            </svg>
          )}
          {resumen.map((m) => {
            const activo = m.clave === delMes.clave;
            const vacio = m.entradas === CERO && m.salidas === CERO;
            const saldo = saldos[meses.indexOf(m.clave)];
            return (
              <button
                key={m.clave}
                type="button"
                aria-pressed={activo}
                aria-label={
                  vacio
                    ? `${nombreDeMes(m.clave)}: sin movimientos`
                    : `${nombreDeMes(m.clave)}: entraron ${soles(m.entradas)} y salieron ${soles(m.salidas)}` +
                      (saldo ? `; cerró con ${formatearImporte(saldo)}` : "")
                }
                onClick={() => elegir(m.clave)}
                className={[
                  "flex h-full flex-col justify-end rounded-t-lg px-1 pt-2 sm:px-2",
                  activo ? "bg-azul-050" : "hover:bg-azul-050/60",
                ].join(" ")}
              >
                {vacio ? (
                  <span aria-hidden="true" className="mx-auto mb-1 h-0.5 w-5 rounded bg-gris-300" />
                ) : (
                  <span className="flex h-full items-end justify-center gap-1">
                    <span
                      className="w-full max-w-5 rounded-t bg-azul-700 transition-[height] duration-500 ease-out"
                      style={{ height: `${altura(m.entradas, maximo)}%` }}
                    />
                    <span
                      className="w-full max-w-5 rounded-t bg-azul-300 transition-[height] duration-500 ease-out"
                      style={{ height: `${altura(m.salidas, maximo)}%` }}
                    />
                  </span>
                )}
              </button>
            );
          })}
        </div>
        <div className="mt-2 grid grid-cols-6 gap-1 sm:gap-3" aria-hidden="true">
          {resumen.map((m) => (
            <span
              key={m.clave}
              className={[
                "text-center text-caption capitalize",
                m.clave === delMes.clave ? "font-bold text-azul-800" : "text-gris-700",
              ].join(" ")}
            >
              {nombreDeMes(m.clave).slice(0, 3)}
            </span>
          ))}
        </div>
      </div>

      {/* En el celular se pliega: primero va el buscador del historial (hallazgo HU-01). */}
      <details className="group lg:hidden">
        <summary className="flex min-h-[44px] cursor-pointer items-center justify-between text-body font-bold text-azul-900 marker:content-none">
          En qué se fue
          <ChevronDown
            aria-hidden="true"
            className="h-5 w-5 text-azul-600 transition-transform group-open:rotate-180"
          />
        </summary>
        <Gastos gastos={gastos} nombre={nombre} salidas={delMes.salidas} />
      </details>
      <div className="hidden lg:block">
        <h3 className="text-body font-bold text-azul-900">En qué se fue</h3>
        <Gastos gastos={gastos} nombre={nombre} salidas={delMes.salidas} />
      </div>
    </section>
  );
}

function Gastos({
  gastos,
  nombre,
  salidas,
}: {
  gastos: ReturnType<typeof gastosPorCategoria>;
  nombre: string;
  salidas: bigint;
}) {
  return (
    <div>
      {gastos.length === 0 ? (
        <p className="mt-2 text-small text-gris-700">No hubo salidas en {nombre}.</p>
      ) : (
        <ul className="mt-4 flex flex-col gap-4">
          {gastos.map(({ categoria, centimos }) => (
            <li key={categoria}>
              <span className="flex justify-between gap-3 text-small">
                <span className="text-gris-900">{categoria}</span>
                <span className="cifra text-gris-900">{soles(centimos)}</span>
              </span>
              <span aria-hidden="true" className="mt-1.5 block h-2 rounded-full bg-azul-050">
                <span
                  className="block h-2 rounded-full bg-dorado-500"
                  style={{ width: `${altura(centimos, salidas)}%` }}
                />
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
