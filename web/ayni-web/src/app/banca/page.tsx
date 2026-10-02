"use client";

import { ArrowLeftRight, ChevronRight, PiggyBank, Wallet } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { TablaDeMovimientos } from "@/componentes/banca/TablaDeMovimientos";
import {
  consultarMiCuenta,
  consultarMovimientos,
  ErrorDeApi,
  type CuentaAbierta,
  type Movimiento,
} from "@/lib/api";
import { formatearImporte } from "@/lib/formato";

/**
 * Resumen de la banca · HU-08. Saldo derivado de los asientos, la cuenta con su TREA y los
 * últimos movimientos. Sigue el lienzo «7 · Banca por Internet · Panel».
 */
export default function PanelDeBanca() {
  const { token } = useSesion();
  const [cuenta, setCuenta] = useState<CuentaAbierta | null>(null);
  const [movimientos, setMovimientos] = useState<Movimiento[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let vivo = true;
    void (async () => {
      try {
        const vigente = await token();
        const [c, m] = await Promise.all([
          consultarMiCuenta(vigente),
          consultarMovimientos(vigente, 10),
        ]);
        if (!vivo) return;
        setCuenta(c);
        setMovimientos(m);
      } catch (fallo) {
        if (!vivo) return;
        setError(
          fallo instanceof ErrorDeApi
            ? (fallo.problema.detail ?? fallo.message)
            : "No pudimos cargar tus saldos. Inténtalo de nuevo en unos segundos.",
        );
      }
    })();
    return () => {
      vivo = false;
    };
  }, [token]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-h1 font-bold text-azul-800">Resumen</h1>
        <p className="text-body text-gris-700">Este es el estado de tu cuenta.</p>
      </div>

      {error && (
        <p
          role="alert"
          className="rounded-lg border border-error bg-blanco p-4 text-body text-error"
        >
          {error}
        </p>
      )}

      <section
        aria-labelledby="titulo-saldo"
        className="rounded-[20px] border border-azul-200 bg-blanco p-7"
      >
        <h2 id="titulo-saldo" className="text-h3 font-bold text-azul-900">
          Saldo total
        </h2>
        <p className="mt-3 text-small text-gris-700">Total en soles</p>
        <p className="cifra text-display text-azul-900" data-testid="saldo-total">
          {cuenta ? formatearImporte(cuenta.saldo, cuenta.moneda) : "—"}
        </p>

        {cuenta && (
          <div className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-azul-200 bg-azul-050 p-4">
            <div className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className="flex h-10 w-10 items-center justify-center rounded-lg bg-azul-700 text-blanco"
              >
                <Wallet className="h-5 w-5" />
              </span>
              <div>
                <p className="text-body font-semibold text-gris-900">Cuenta Ayni Soles</p>
                <p className="text-small text-gris-700" data-testid="numero-de-cuenta">
                  {cuenta.numeroFormateado}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              {cuenta.trea && (
                <span className="rounded-full bg-dorado-100 px-3 py-1 text-caption font-bold text-dorado-800">
                  TREA {cuenta.trea} %
                </span>
              )}
              <span className="cifra text-h3 text-azul-900">
                {formatearImporte(cuenta.saldo, cuenta.moneda)}
              </span>
            </div>
          </div>
        )}
      </section>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <AccesoRapido
          href="/banca/transferir"
          titulo="Transferir"
          detalle="A otra cuenta Ayni, al instante"
          Icono={ArrowLeftRight}
        />
        <AccesoRapido
          href="/banca/depositar"
          titulo="Depositar"
          detalle="Depósito simulado (entorno académico)"
          Icono={PiggyBank}
        />
      </div>

      <section
        aria-labelledby="titulo-movimientos"
        className="overflow-hidden rounded-[20px] border border-azul-200 bg-blanco"
      >
        <h2 id="titulo-movimientos" className="px-6 py-5 text-h3 font-bold text-azul-900">
          Últimos movimientos
        </h2>
        {movimientos ? (
          <TablaDeMovimientos movimientos={movimientos} />
        ) : (
          <p role="status" className="px-6 pb-6 text-body text-gris-700">
            Cargando…
          </p>
        )}
      </section>
    </div>
  );
}

function AccesoRapido({
  href,
  titulo,
  detalle,
  Icono,
}: {
  href: string;
  titulo: string;
  detalle: string;
  Icono: typeof Wallet;
}) {
  return (
    <Link
      href={href}
      className="flex items-center gap-4 rounded-[16px] border border-azul-200 bg-blanco p-5 hover:border-azul-400"
    >
      <span
        aria-hidden="true"
        className="flex h-11 w-11 items-center justify-center rounded-xl bg-azul-100 text-azul-700"
      >
        <Icono className="h-5 w-5" />
      </span>
      <span className="flex-1">
        <span className="block text-body font-semibold text-gris-900">{titulo}</span>
        <span className="block text-small text-gris-700">{detalle}</span>
      </span>
      <ChevronRight aria-hidden="true" className="h-5 w-5 text-gris-500" />
    </Link>
  );
}
