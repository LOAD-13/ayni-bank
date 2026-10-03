import { CircleCheck } from "lucide-react";
import Link from "next/link";

import type { Comprobante } from "@/lib/api";
import { formatearFecha, formatearImporte } from "@/lib/formato";

/** El comprobante de una operación terminada. Las cuentas se muestran enmascaradas. */
export function TarjetaDeComprobante({ comprobante }: { comprobante: Comprobante }) {
  const esDeposito = comprobante.tipo === "DEPOSITO_SIMULADO";
  const filas: [string, string][] = [
    ["Operación", esDeposito ? "Depósito simulado" : "Transferencia entre cuentas Ayni"],
    ["Desde", comprobante.cuentaOrigen],
    ["Hacia", comprobante.cuentaDestino],
    ["Concepto", comprobante.concepto],
    ["Fecha", formatearFecha(comprobante.registradoEn)],
    ["Saldo disponible", formatearImporte(comprobante.saldoDisponible, comprobante.moneda)],
    ["Nº de operación", comprobante.movimientoId],
  ];

  return (
    <section
      aria-labelledby="titulo-comprobante"
      className="mx-auto w-full max-w-2xl rounded-[20px] border border-azul-200 bg-blanco p-8 shadow-sm"
    >
      <div className="flex items-center gap-3">
        <CircleCheck aria-hidden="true" className="h-8 w-8 text-exito" />
        <h1 id="titulo-comprobante" className="text-h2 font-bold text-azul-800">
          {esDeposito ? "Depósito realizado" : "Transferencia realizada"}
        </h1>
      </div>

      <p className="cifra mt-5 text-display text-azul-900" data-testid="importe-comprobante">
        {formatearImporte(comprobante.importe, comprobante.moneda)}
      </p>

      <dl className="mt-6 divide-y divide-azul-100 border-y border-azul-100">
        {filas.map(([clave, valor]) => (
          <div key={clave} className="flex justify-between gap-6 py-3">
            <dt className="text-small text-gris-700">{clave}</dt>
            <dd className="text-right text-small font-medium break-all text-gris-900">{valor}</dd>
          </div>
        ))}
      </dl>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <Link
          href="/banca"
          className="inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-6 text-body font-semibold text-blanco hover:bg-azul-800"
        >
          Volver al resumen
        </Link>
      </div>
    </section>
  );
}
