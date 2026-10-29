"use client";

import { Download } from "lucide-react";
import { useState } from "react";

import { useSesion } from "@/componentes/banca/SesionDeBanca";
import { descargarComprobante } from "@/lib/api";

interface Props {
  movimientoId: string;
  /** «boton» en la tarjeta del comprobante; «enlace» discreto en la tabla de movimientos. */
  variante?: "boton" | "enlace";
}

/** HU-07: descarga el comprobante en PDF que genera el banco. */
export function DescargarComprobante({ movimientoId, variante = "boton" }: Props) {
  const { token } = useSesion();
  const [descargando, setDescargando] = useState(false);
  const [error, setError] = useState(false);

  async function descargar() {
    setDescargando(true);
    setError(false);
    try {
      const pdf = await descargarComprobante(await token(), movimientoId);
      const url = URL.createObjectURL(pdf);
      const enlace = document.createElement("a");
      enlace.href = url;
      enlace.download = `comprobante-ayni-${movimientoId}.pdf`;
      enlace.click();
      URL.revokeObjectURL(url);
    } catch {
      setError(true);
    } finally {
      setDescargando(false);
    }
  }

  const clases =
    variante === "boton"
      ? "inline-flex min-h-[44px] items-center justify-center gap-2 rounded-full border border-azul-700 px-6 text-body font-semibold text-azul-700 hover:bg-azul-050 disabled:opacity-60"
      : "mt-1 inline-flex items-center gap-1 text-caption font-semibold text-azul-600 hover:underline disabled:opacity-60";

  return (
    <span
      className={variante === "boton" ? "flex flex-col gap-1" : "inline-flex flex-col items-end"}
    >
      <button
        type="button"
        onClick={descargar}
        disabled={descargando}
        aria-busy={descargando}
        className={clases}
      >
        <Download aria-hidden="true" className={variante === "boton" ? "h-4 w-4" : "h-3 w-3"} />
        {descargando
          ? "Descargando…"
          : variante === "boton"
            ? "Descargar comprobante"
            : "Comprobante"}
      </button>
      {error && (
        <span role="alert" className="text-caption text-error">
          No pudimos descargarlo. Inténtalo de nuevo.
        </span>
      )}
    </span>
  );
}
