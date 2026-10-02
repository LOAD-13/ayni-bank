import { ArrowDownLeft, ArrowUpRight } from "lucide-react";

import type { Movimiento } from "@/lib/api";
import { formatearFecha, formatearImporte } from "@/lib/formato";

/** Últimos movimientos de la cuenta, del más reciente al más antiguo. */
export function TablaDeMovimientos({ movimientos }: { movimientos: Movimiento[] }) {
  if (movimientos.length === 0) {
    return (
      <p className="px-6 py-10 text-center text-body text-gris-700">
        Todavía no hay movimientos. Haz un depósito o recibe una transferencia para empezar.
      </p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-left">
        <caption className="sr-only">Últimos movimientos de tu cuenta</caption>
        <thead className="bg-azul-050 text-small text-gris-700">
          <tr>
            <th scope="col" className="px-6 py-3 font-semibold">
              Concepto
            </th>
            <th scope="col" className="px-6 py-3 font-semibold">
              Fecha
            </th>
            <th scope="col" className="px-6 py-3 text-right font-semibold">
              Importe
            </th>
          </tr>
        </thead>
        <tbody>
          {movimientos.map((m) => {
            const abono = m.tipo === "ABONO";
            const Icono = abono ? ArrowDownLeft : ArrowUpRight;
            return (
              <tr key={`${m.movimientoId}-${m.tipo}`} className="border-t border-azul-100">
                <td className="px-6 py-4">
                  <span className="flex items-center gap-3">
                    <span
                      aria-hidden="true"
                      className={[
                        "flex h-9 w-9 shrink-0 items-center justify-center rounded-full",
                        abono ? "bg-azul-100 text-exito" : "bg-azul-050 text-azul-700",
                      ].join(" ")}
                    >
                      <Icono className="h-4 w-4" />
                    </span>
                    <span className="text-body font-medium text-gris-900">{m.concepto}</span>
                  </span>
                </td>
                <td className="px-6 py-4 text-small text-gris-700">
                  {formatearFecha(m.registradoEn)}
                </td>
                <td className="px-6 py-4 text-right">
                  <span className={`cifra block ${abono ? "text-exito" : "text-gris-900"}`}>
                    {abono ? "+ " : "− "}
                    {formatearImporte(m.importe, m.moneda)}
                  </span>
                  <span className="text-caption text-gris-700">{abono ? "Abono" : "Cargo"}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
