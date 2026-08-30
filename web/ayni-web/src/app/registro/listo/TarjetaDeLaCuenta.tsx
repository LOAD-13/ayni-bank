"use client";

import { Copy } from "lucide-react";
import { useState } from "react";

import type { CuentaAbierta } from "@/lib/api";

/**
 * La tarjeta azul con los datos de la cuenta, según el diseño aprobado en pen.dev.
 *
 * El número y el CCI llevan botón de copiar porque son cifras largas que la gente tiene que
 * pegar en el formulario de otro banco. Se copia el valor **sin guiones**: los guiones son
 * para leerlo, no para pegarlo, y varias bancas rechazan el CCI si llegan.
 */
export function TarjetaDeLaCuenta({ cuenta }: { cuenta: CuentaAbierta }) {
  return (
    <div className="mt-7 rounded-[16px] bg-gradient-to-br from-azul-800 to-azul-tarjeta p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-[14px] font-bold text-blanco">
          Cuenta Ayni · {cuenta.moneda === "PEN" ? "Soles" : "Dólares"}
        </p>
        {cuenta.trea && (
          <p className="rounded-full bg-oro px-3 py-1 text-[12px] font-bold text-azul-900">
            TREA {cuenta.trea} %
          </p>
        )}
      </div>

      <dl className="mt-5 flex flex-col">
        <Fila
          etiqueta="Número de cuenta"
          mostrado={cuenta.numeroFormateado}
          copiable={cuenta.numero}
        />
        <Fila
          etiqueta="Código de cuenta interbancario (CCI)"
          mostrado={cuenta.cciFormateado}
          copiable={cuenta.cci}
        />
        <Fila
          etiqueta="Comisión de mantenimiento"
          mostrado={`S/ ${cuenta.comisionDeMantenimiento}`}
        />
      </dl>
    </div>
  );
}

function Fila({
  etiqueta,
  mostrado,
  copiable,
}: {
  etiqueta: string;
  mostrado: string;
  copiable?: string;
}) {
  const [copiado, setCopiado] = useState(false);

  async function copiar() {
    if (!copiable) return;
    await navigator.clipboard.writeText(copiable);
    setCopiado(true);
    setTimeout(() => setCopiado(false), 2000);
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-t border-blanco/10 py-3.5 first:border-t-0 first:pt-0">
      <dt className="text-[13px] text-azul-300">{etiqueta}</dt>
      <dd className="flex items-center gap-2.5">
        <span className="cifra text-[14.5px] text-blanco">{mostrado}</span>
        {copiable && (
          <button
            type="button"
            onClick={copiar}
            aria-label={copiado ? `${etiqueta} copiado` : `Copiar ${etiqueta}`}
            className="text-azul-300 hover:text-blanco"
          >
            <Copy aria-hidden="true" className="h-3.5 w-3.5" />
          </button>
        )}
      </dd>
    </div>
  );
}
