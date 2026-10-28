import { TrendingUp } from "lucide-react";

import type { CuentaAbierta, RendimientoDeCuenta } from "@/lib/api";
import { formatearImporte, rendimientoMensualEstimado } from "@/lib/formato";

/** Cuantos devengos se muestran en el detalle: una semana basta para ver la cuenta. */
const DEVENGOS_VISIBLES = 7;

/** "2026-10-15" → "15/10/2026", sin pasar por Date: una fecha sin hora no tiene huso. */
function fechaCorta(iso: string): string {
  const [anio, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${anio}`;
}

/**
 * HU-06: lo que rinde la cuenta.
 *
 * Lo ganado sale del devengo real de cada dia, no de una estimacion. Mientras no haya
 * devengos (cuenta recien abierta o sin saldo), se muestra la estimacion con la TREA
 * vigente, rotulada como tal.
 */
export function PanelDeRendimiento({
  cuenta,
  rendimiento,
  oculto,
}: {
  cuenta: CuentaAbierta | null;
  rendimiento: RendimientoDeCuenta | null;
  oculto: boolean;
}) {
  const trea = rendimiento?.trea ?? cuenta?.trea ?? null;
  const estimado = cuenta && trea ? rendimientoMensualEstimado(cuenta.saldo, trea) : null;
  const hayDevengos = (rendimiento?.devengos.length ?? 0) > 0;
  const moneda = rendimiento?.moneda ?? "PEN";
  const mostrar = (importe: string) =>
    oculto ? `${moneda === "PEN" ? "S/" : moneda} ••••` : formatearImporte(importe, moneda);

  let titular: string;
  if (hayDevengos && rendimiento) {
    titular = `Este mes ya ganaste ${mostrar(rendimiento.devengadoDelMesRedondeado)}`;
  } else if (estimado === null) {
    titular = "Tu dinero empieza a rendir con tu primer depósito";
  } else {
    titular = oculto
      ? "Tu dinero gana S/ •••• al mes"
      : `Tu dinero gana unos ${formatearImporte(estimado, "PEN")} al mes`;
  }

  return (
    <section
      aria-labelledby="titulo-rendimiento"
      className="rounded-[20px] border border-exito/30 bg-[#EAF4EF] p-7"
    >
      <p id="titulo-rendimiento" className="flex items-center gap-3 text-body font-bold text-exito">
        <span
          aria-hidden="true"
          className="flex h-9 w-9 items-center justify-center rounded-lg bg-exito text-blanco"
        >
          <TrendingUp className="h-5 w-5" />
        </span>
        {hayDevengos ? "Rendimiento del mes" : "Rendimiento estimado del mes"}
      </p>
      <p className="mt-4 text-h2 font-bold text-azul-900">{titular}</p>

      {trea && (
        <p className="mt-2 text-small text-gris-800">
          {rendimiento?.treaIgualATea !== false
            ? `TREA ${trea} % = TEA ${rendimiento?.tea ?? trea} %. No cobramos comisión de mantenimiento, así que lo que anunciamos es exactamente lo que ganas.`
            : `TREA ${trea} % · TEA ${rendimiento?.tea} %.`}{" "}
          El interés se devenga cada día sobre tu saldo de cierre y se abona el último día del mes.
        </p>
      )}

      {rendimiento && hayDevengos && (
        <>
          <p className="mt-3 text-small text-gris-800">
            Intereses abonados desde que abriste tu cuenta:{" "}
            <strong>{mostrar(rendimiento.abonadoTotal)}</strong>
          </p>
          <details className="mt-4 rounded-xl bg-blanco/70 p-4">
            <summary className="cursor-pointer text-small font-bold text-azul-800">
              Ver cómo se calcula cada día
            </summary>
            <table className="mt-3 w-full text-left text-small">
              <caption className="sr-only">
                Últimos intereses devengados, con su saldo base y la tasa aplicada
              </caption>
              <thead>
                <tr className="text-gris-700">
                  <th scope="col" className="py-1 font-normal">
                    Día
                  </th>
                  <th scope="col" className="py-1 font-normal">
                    Saldo de cierre
                  </th>
                  <th scope="col" className="py-1 font-normal">
                    TEA
                  </th>
                  <th scope="col" className="py-1 text-right font-normal">
                    Interés
                  </th>
                </tr>
              </thead>
              <tbody>
                {rendimiento.devengos.slice(0, DEVENGOS_VISIBLES).map((d) => (
                  <tr key={d.fecha} className="border-t border-exito/15">
                    <td className="py-1">{fechaCorta(d.fecha)}</td>
                    <td className="py-1">{mostrar(d.saldoBase)}</td>
                    <td className="py-1">{d.tea} %</td>
                    <td className="py-1 text-right tabular-nums">
                      {oculto ? "••••" : `${moneda === "PEN" ? "S/" : moneda} ${d.importe}`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="mt-2 text-[12px] text-gris-700">
              Interés del día = saldo de cierre × ((1 + TEA)^(1/360) − 1), con año comercial de 360
              días.
            </p>
          </details>
        </>
      )}
    </section>
  );
}
