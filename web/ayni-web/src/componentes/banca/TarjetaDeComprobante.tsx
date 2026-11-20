"use client";

import { Check } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef } from "react";

import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import { DescargarComprobante } from "@/componentes/banca/DescargarComprobante";
import { LogroDePrimeraOperacion } from "@/componentes/banca/LogroDePrimeraOperacion";
import type { Comprobante } from "@/lib/api";
import { formatearFecha, formatearImporte } from "@/lib/formato";
import { conceptoLegible } from "@/lib/movimientos";

/**
 * El comprobante de una operación terminada, con la misma cara que el PDF que genera el
 * banco: logo, importe destacado y las cuentas enmascaradas.
 *
 * Al aparecer se lleva el foco al título: el formulario desaparece y, sin eso, un lector de
 * pantalla no anunciaría que la operación terminó (hallazgo AC-03 de las pruebas).
 */
export function TarjetaDeComprobante({ comprobante }: { comprobante: Comprobante }) {
  const titulo = useRef<HTMLHeadingElement>(null);
  useEffect(() => titulo.current?.focus(), []);

  const esDeposito = comprobante.tipo === "DEPOSITO_SIMULADO";
  const filas: [string, string][] = [
    ["Operación", esDeposito ? "Depósito simulado" : "Transferencia entre cuentas Ayni"],
    ["Desde", conceptoLegible(comprobante.cuentaOrigen)],
    ["Hacia", conceptoLegible(comprobante.cuentaDestino)],
    ["Concepto", conceptoLegible(comprobante.concepto)],
    ["Fecha y hora", formatearFecha(comprobante.registradoEn)],
    ["Saldo disponible", formatearImporte(comprobante.saldoDisponible, comprobante.moneda)],
    ["Nº de operación", comprobante.movimientoId],
  ];

  return (
    <section
      aria-labelledby="titulo-comprobante"
      className="mx-auto w-full max-w-2xl overflow-hidden rounded-[20px] border border-azul-200 bg-blanco shadow-[0_8px_24px_-12px_rgb(6_68_117/0.25)]"
    >
      <div className="flex items-center justify-between gap-4 bg-azul-800 px-6 py-4 sm:px-8">
        <LogotipoAyni altura={15} tono="claro" />
        <span className="text-small text-azul-100">Comprobante</span>
      </div>

      <div className="flex flex-col items-center gap-3 border-b border-dashed border-azul-200 px-6 pt-8 pb-7 text-center sm:px-8">
        <span
          aria-hidden="true"
          className="flex h-12 w-12 items-center justify-center rounded-full bg-exito/10 text-exito"
        >
          <Check className="h-6 w-6" />
        </span>
        <h1
          id="titulo-comprobante"
          ref={titulo}
          tabIndex={-1}
          className="text-h2 font-bold text-azul-800 outline-none"
        >
          {esDeposito ? "Depósito realizado" : "Transferencia realizada"}
        </h1>
        <p className="cifra text-display text-azul-900" data-testid="importe-comprobante">
          {formatearImporte(comprobante.importe, comprobante.moneda)}
        </p>
        <div className="w-full empty:hidden">
          <LogroDePrimeraOperacion />
        </div>
      </div>

      <dl className="divide-y divide-azul-100 px-6 sm:px-8">
        {filas.map(([clave, valor]) => (
          <div key={clave} className="flex justify-between gap-6 py-3">
            <dt className="shrink-0 text-small text-gris-700">{clave}</dt>
            <dd className="text-right text-small font-medium break-all text-gris-900">{valor}</dd>
          </div>
        ))}
      </dl>

      <div className="flex flex-col gap-3 border-t border-azul-100 px-6 py-6 sm:flex-row sm:px-8">
        <Link
          href="/banca"
          className="inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-6 text-body font-semibold text-blanco hover:bg-azul-800"
        >
          Volver al resumen
        </Link>
        <DescargarComprobante movimientoId={comprobante.movimientoId} />
        <Link
          href="/banca/movimientos"
          className="inline-flex min-h-[44px] items-center justify-center rounded-full px-4 text-body font-semibold text-azul-700 hover:bg-azul-050"
        >
          Ver mis movimientos
        </Link>
      </div>
    </section>
  );
}
