"use client";

import { ArrowDownLeft, ArrowLeft, ArrowUpRight, Check, Copy, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { DescargarComprobante } from "@/componentes/banca/DescargarComprobante";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { consultarMovimiento, ErrorDeApi, type Comprobante } from "@/lib/api";
import { formatearFecha, formatearImporte } from "@/lib/formato";
import { categoriaDe, conceptoLegible } from "@/lib/movimientos";

const OPERACION: Record<Comprobante["tipo"], string> = {
  TRANSFERENCIA: "Transferencia",
  DEPOSITO_SIMULADO: "Depósito simulado",
  INTERES: "Intereses de tu cuenta",
  CONVERSION: "Cambio de moneda",
};

type Estado =
  | { tipo: "cargando" }
  | { tipo: "listo"; movimiento: Comprobante }
  | { tipo: "no-existe" }
  | { tipo: "error" };

/**
 * El detalle de un movimiento (HU-07): qué fue, cuándo, entre qué cuentas y su comprobante.
 * Un movimiento ajeno responde 404 igual que uno inexistente, y aquí se trata igual.
 */
export default function DetalleDeMovimiento() {
  const { id } = useParams<{ id: string }>();
  const { token } = useSesion();
  const [estado, setEstado] = useState<Estado>({ tipo: "cargando" });

  const cargar = useCallback(async () => {
    setEstado({ tipo: "cargando" });
    try {
      setEstado({ tipo: "listo", movimiento: await consultarMovimiento(await token(), id) });
    } catch (fallo) {
      setEstado(
        fallo instanceof ErrorDeApi && fallo.estado === 404
          ? { tipo: "no-existe" }
          : { tipo: "error" },
      );
    }
  }, [token, id]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-5">
      <Link
        href="/banca/movimientos"
        className="inline-flex min-h-[44px] w-fit items-center gap-2 text-small font-semibold text-azul-600 hover:underline"
      >
        <ArrowLeft aria-hidden="true" className="h-4 w-4" /> Movimientos
      </Link>

      {estado.tipo === "cargando" ? (
        <div
          role="status"
          aria-live="polite"
          className="h-[420px] animate-pulse rounded-[20px] border border-azul-200 bg-blanco"
        >
          <span className="sr-only">Cargando el movimiento…</span>
        </div>
      ) : estado.tipo === "no-existe" ? (
        <section className="rounded-[20px] border border-azul-200 bg-blanco p-8">
          <h1 className="text-h2 font-bold text-azul-900">No encontramos este movimiento</h1>
          <p className="mt-2 text-body text-gris-700">
            Puede que el enlace esté incompleto o que el movimiento no sea de tu cuenta. Búscalo en
            tu historial.
          </p>
        </section>
      ) : estado.tipo === "error" ? (
        <section
          role="alert"
          aria-live="assertive"
          className="rounded-[20px] border border-dorado-500 bg-dorado-100/60 p-6"
        >
          <p className="text-body font-bold text-dorado-800">No pudimos cargar el movimiento</p>
          <p className="mt-1 text-small text-gris-900">Inténtalo de nuevo en unos segundos.</p>
          <button
            type="button"
            onClick={() => void cargar()}
            className="mt-4 flex min-h-[44px] items-center gap-2 rounded-full bg-azul-800 px-5 text-small font-semibold text-blanco hover:bg-azul-900"
          >
            <RefreshCw aria-hidden="true" className="h-4 w-4" /> Reintentar
          </button>
        </section>
      ) : (
        <Detalle movimiento={estado.movimiento} />
      )}
    </div>
  );
}

function Detalle({ movimiento: m }: { movimiento: Comprobante }) {
  const abono = m.sentido === "ABONO";
  const Icono = abono ? ArrowDownLeft : ArrowUpRight;
  const operacion = OPERACION[m.tipo] ?? "Movimiento";
  const categoria = categoriaDe(m.concepto);
  const filas: [string, string][] = [
    ["Fecha y hora", formatearFecha(m.registradoEn)],
    ["Operación", operacion],
    // La categoría solo añade algo cuando no repite la operación («A otro banco», «Tarjeta»).
    ...(categoria !== operacion ? [["Categoría", categoria] as [string, string]] : []),
    ["Desde", conceptoLegible(m.cuentaOrigen)],
    ["Hacia", conceptoLegible(m.cuentaDestino)],
  ];

  return (
    <article
      aria-labelledby="titulo-movimiento"
      className="overflow-hidden rounded-[20px] border border-azul-200 bg-blanco"
    >
      <header className="flex flex-col items-center gap-3 border-b border-azul-100 bg-azul-050 px-6 py-8 text-center">
        <span
          aria-hidden="true"
          className={[
            "flex h-14 w-14 items-center justify-center rounded-full",
            abono ? "bg-exito/10 text-exito" : "bg-azul-100 text-azul-700",
          ].join(" ")}
        >
          <Icono className="h-6 w-6" />
        </span>
        <h1 id="titulo-movimiento" className="text-h3 font-bold text-azul-900 text-balance">
          {conceptoLegible(m.concepto)}
        </h1>
        <p
          className={`cifra text-display ${abono ? "text-exito" : "text-azul-900"}`}
          data-testid="importe-del-movimiento"
        >
          <span className="sr-only">{abono ? "Entrada de " : "Salida de "}</span>
          <span aria-hidden="true">{m.sentido ? (abono ? "+ " : "− ") : ""}</span>
          {formatearImporte(m.importe, m.moneda)}
        </p>
        <span className="flex items-center gap-1.5 rounded-full bg-blanco px-3 py-1 text-caption font-bold text-exito">
          <Check aria-hidden="true" className="h-3.5 w-3.5" /> Completado
        </span>
      </header>

      <dl className="divide-y divide-azul-100 px-6">
        {filas.map(([clave, valor]) => (
          <div key={clave} className="flex justify-between gap-6 py-3.5">
            <dt className="text-small text-gris-700">{clave}</dt>
            <dd className="text-right text-small font-medium break-words text-gris-900">{valor}</dd>
          </div>
        ))}
        <NumeroDeOperacion id={m.movimientoId} />
      </dl>

      <div className="flex flex-col gap-3 border-t border-azul-100 px-6 py-6 sm:flex-row sm:items-start">
        <DescargarComprobante movimientoId={m.movimientoId} />
      </div>
    </article>
  );
}

function NumeroDeOperacion({ id }: { id: string }) {
  const [copiado, setCopiado] = useState(false);
  return (
    <div className="flex items-center justify-between gap-4 py-3.5">
      <dt className="shrink-0 text-small whitespace-nowrap text-gris-700">Nº de operación</dt>
      <dd className="flex min-w-0 items-center gap-2">
        <span className="cifra truncate text-small text-gris-900">{id}</span>
        <button
          type="button"
          onClick={() => void navigator.clipboard?.writeText(id).then(() => setCopiado(true))}
          aria-label={copiado ? "Número de operación copiado" : "Copiar el número de operación"}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-azul-600 hover:bg-azul-050"
        >
          {copiado ? (
            <Check aria-hidden="true" className="h-4 w-4" />
          ) : (
            <Copy aria-hidden="true" className="h-4 w-4" />
          )}
        </button>
        <span aria-live="polite" className="sr-only">
          {copiado ? "Copiado" : ""}
        </span>
      </dd>
    </div>
  );
}
