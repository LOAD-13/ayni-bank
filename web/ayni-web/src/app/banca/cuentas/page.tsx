"use client";

import { ArrowLeftRight, Check, Copy, ReceiptText, RefreshCw, Wallet } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { PanelDeRendimiento } from "@/componentes/banca/PanelDeRendimiento";
import { useSesion } from "@/componentes/banca/SesionDeBanca";
import {
  consultarMiCuenta,
  consultarRendimiento,
  ErrorDeApi,
  type CuentaAbierta,
  type RendimientoDeCuenta,
} from "@/lib/api";
import { formatearImporte } from "@/lib/formato";

type Estado =
  | { tipo: "cargando" }
  | { tipo: "lista"; cuenta: CuentaAbierta; rendimiento: RendimientoDeCuenta | null }
  | { tipo: "sin-cuenta" }
  | { tipo: "error" };

const MONEDA: Record<string, string> = { PEN: "Soles", USD: "Dólares" };

/**
 * Cuentas: los datos completos de la cuenta del titular (número, CCI, tasas, comisión y
 * estado) y lo que lleva ganado en intereses. Es la ficha que se comparte para recibir dinero.
 */
export default function PaginaDeCuentas() {
  const { token } = useSesion();
  const [estado, setEstado] = useState<Estado>({ tipo: "cargando" });

  const cargar = useCallback(async () => {
    setEstado({ tipo: "cargando" });
    try {
      const vigente = await token();
      const [cuenta, rendimiento] = await Promise.all([
        consultarMiCuenta(vigente),
        consultarRendimiento(vigente).catch(() => null),
      ]);
      setEstado({ tipo: "lista", cuenta, rendimiento });
    } catch (fallo) {
      setEstado(
        fallo instanceof ErrorDeApi && fallo.problema.codigo === "SIN_CUENTA"
          ? { tipo: "sin-cuenta" }
          : { tipo: "error" },
      );
    }
  }, [token]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-h1 font-bold text-azul-800">Cuentas</h1>
        <p className="text-body text-gris-700">
          Los datos de tu cuenta para recibir dinero y lo que te rinde cada día.
        </p>
      </header>

      {estado.tipo === "cargando" ? (
        <div
          role="status"
          aria-live="polite"
          className="h-80 animate-pulse rounded-[20px] border border-azul-200 bg-blanco"
        >
          <span className="sr-only">Cargando tu cuenta…</span>
        </div>
      ) : estado.tipo === "sin-cuenta" ? (
        <section className="rounded-[20px] border border-azul-200 bg-blanco p-8">
          <h2 className="text-h2 font-bold text-azul-900">Tu cuenta aún no está abierta</h2>
          <p className="mt-2 text-body text-gris-700">
            Termina la verificación de identidad y tu cuenta aparecerá aquí con su número y su CCI.
          </p>
          <Link
            href="/banca"
            className="mt-6 inline-flex min-h-[44px] items-center rounded-full bg-azul-800 px-6 text-body font-semibold text-blanco hover:bg-azul-900"
          >
            Ir al resumen
          </Link>
        </section>
      ) : estado.tipo === "error" ? (
        <section
          role="alert"
          aria-live="assertive"
          className="rounded-[20px] border border-dorado-500 bg-dorado-100/60 p-6"
        >
          <p className="text-body font-bold text-dorado-800">No pudimos cargar tu cuenta</p>
          <p className="mt-1 text-small text-gris-900">
            Tu dinero está seguro: lo único que falló es la consulta.
          </p>
          <button
            type="button"
            onClick={() => void cargar()}
            className="mt-4 flex min-h-[44px] items-center gap-2 rounded-full bg-azul-800 px-5 text-small font-semibold text-blanco hover:bg-azul-900"
          >
            <RefreshCw aria-hidden="true" className="h-4 w-4" /> Reintentar
          </button>
        </section>
      ) : (
        <>
          <FichaDeCuenta cuenta={estado.cuenta} />
          <PanelDeRendimiento
            cuenta={estado.cuenta}
            rendimiento={estado.rendimiento}
            oculto={false}
          />
        </>
      )}
    </div>
  );
}

function FichaDeCuenta({ cuenta }: { cuenta: CuentaAbierta }) {
  const activa = cuenta.estado === "ACTIVA";
  const moneda = MONEDA[cuenta.moneda] ?? cuenta.moneda;
  return (
    <section
      aria-labelledby="titulo-cuenta"
      className="overflow-hidden rounded-[20px] border border-azul-200 bg-blanco"
    >
      <div className="flex flex-wrap items-center justify-between gap-4 bg-azul-800 px-6 py-6 text-blanco sm:px-7">
        <div className="flex items-center gap-4">
          <span
            aria-hidden="true"
            className="flex h-12 w-12 items-center justify-center rounded-xl bg-blanco/10"
          >
            <Wallet className="h-6 w-6" />
          </span>
          <div>
            <h2 id="titulo-cuenta" className="text-h3 font-bold">
              Cuenta Ayni {moneda}
            </h2>
            <p className="text-small text-azul-100">
              Ahorro remunerado · {activa ? "Activa" : cuenta.estado.toLowerCase()}
            </p>
          </div>
        </div>
        <div className="text-right">
          <p className="text-small text-azul-100">Saldo disponible</p>
          <p className="cifra text-h1" data-testid="saldo-de-la-cuenta">
            {formatearImporte(cuenta.saldo, cuenta.moneda)}
          </p>
        </div>
      </div>

      <dl className="grid grid-cols-1 gap-x-8 px-6 sm:grid-cols-2 sm:px-7">
        <DatoCopiable
          etiqueta="Número de cuenta"
          valor={cuenta.numeroFormateado}
          copia={cuenta.numero}
        />
        <DatoCopiable
          etiqueta="Código interbancario (CCI)"
          valor={cuenta.cciFormateado}
          copia={cuenta.cci}
        />
        <Dato etiqueta="Moneda" valor={`${moneda} (${cuenta.moneda})`} />
        <Dato etiqueta="TREA" valor={cuenta.trea ? `${cuenta.trea} %` : "—"} />
        <Dato
          etiqueta="Comisión de mantenimiento"
          valor={formatearImporte(cuenta.comisionDeMantenimiento, cuenta.moneda)}
        />
        <Dato etiqueta="Intereses" valor="Se calculan cada día y se abonan cada mes" />
      </dl>

      <div className="flex flex-col gap-3 border-t border-azul-100 px-6 py-5 sm:flex-row sm:px-7">
        <Link
          href="/banca/movimientos"
          className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-full bg-azul-800 px-6 text-body font-semibold text-blanco hover:bg-azul-900"
        >
          <ReceiptText aria-hidden="true" className="h-4 w-4" /> Ver movimientos
        </Link>
        <Link
          href="/banca/transferir"
          className="inline-flex min-h-[44px] items-center justify-center gap-2 rounded-full border border-azul-700 px-6 text-body font-semibold text-azul-700 hover:bg-azul-050"
        >
          <ArrowLeftRight aria-hidden="true" className="h-4 w-4" /> Transferir
        </Link>
      </div>
    </section>
  );
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-b border-azul-100 py-4">
      <dt className="text-small text-gris-700">{etiqueta}</dt>
      <dd className="text-body font-medium text-gris-900">{valor}</dd>
    </div>
  );
}

function DatoCopiable({
  etiqueta,
  valor,
  copia,
}: {
  etiqueta: string;
  valor: string;
  copia: string;
}) {
  const [copiado, setCopiado] = useState(false);
  return (
    <div className="flex flex-col gap-0.5 border-b border-azul-100 py-4">
      <dt className="text-small text-gris-700">{etiqueta}</dt>
      <dd className="flex items-center justify-between gap-3">
        <span className="cifra min-w-0 text-body break-all text-azul-900">{valor}</span>
        <button
          type="button"
          onClick={() => void navigator.clipboard?.writeText(copia).then(() => setCopiado(true))}
          className="flex min-h-[44px] shrink-0 items-center gap-1.5 rounded-full px-3 text-small font-semibold text-azul-600 hover:bg-azul-050"
        >
          {copiado ? (
            <Check aria-hidden="true" className="h-4 w-4" />
          ) : (
            <Copy aria-hidden="true" className="h-4 w-4" />
          )}
          <span aria-live="polite">{copiado ? "Copiado" : "Copiar"}</span>
          <span className="sr-only"> {etiqueta}</span>
        </button>
      </dd>
    </div>
  );
}
