"use client";

import { BookmarkCheck } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { IndicadorDeProgreso } from "@/componentes/IndicadorDeProgreso";
import { LogotipoAyni } from "@/componentes/LogotipoAyni";
import type { TipoDeDocumentoKyc } from "@/lib/api";

import { CapturaDeDocumento } from "./CapturaDeDocumento";

interface Props {
  solicitudId: string | undefined;
  pasoActual: number;
  tipoDocumento: TipoDeDocumentoKyc;
  cara: string;
  /** A dónde navegar cuando la subida termina bien. */
  siguienteHref: string;
}

/**
 * El marco de página (cabecera, progreso, tarjeta) de un paso de captura de DNI, separado
 * de {@link CapturaDeDocumento} porque esta parte sabe de rutas y esa no sabe nada de
 * navegación: es la misma frontera que ya separa `EstadoDeLaCuenta` de `registro/listo/page.tsx`.
 */
export function PasoDeCapturaDeDni({
  solicitudId,
  pasoActual,
  tipoDocumento,
  cara,
  siguienteHref,
}: Props) {
  const router = useRouter();

  return (
    <div className="min-h-screen bg-azul-050">
      <header className="border-b border-gris-300 bg-blanco">
        <div className="mx-auto flex max-w-[1180px] items-center justify-between gap-4 px-6 py-3.5">
          <Link href="/">
            <LogotipoAyni altura={16} tono="oscuro" />
          </Link>
          <div className="flex items-center gap-4">
            <span className="hidden text-[13.5px] text-gris-500 sm:inline">
              Paso <strong className="font-bold text-azul-700">{pasoActual}</strong> de 5
            </span>
            <Link
              href="/"
              className="inline-flex items-center gap-2 rounded-full border border-gris-300 px-4 py-2 text-[13.5px] font-medium text-gris-700 hover:bg-gris-100"
            >
              <BookmarkCheck aria-hidden="true" className="h-4 w-4" />
              Guardar y salir
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto flex max-w-[1180px] flex-col items-center gap-9 px-6 py-9">
        <IndicadorDeProgreso pasoActual={pasoActual} />

        <div className="w-full max-w-[560px] rounded-[18px] border border-gris-300 bg-blanco p-6 shadow-sm sm:p-9">
          {solicitudId ? (
            <CapturaDeDocumento
              solicitudId={solicitudId}
              tipoDocumento={tipoDocumento}
              cara={cara}
              onCompletado={() => router.push(siguienteHref)}
            />
          ) : (
            <div className="text-center">
              <h1 className="text-[22px] font-bold text-azul-800">
                No sabemos de qué solicitud hablas
              </h1>
              <p className="mt-2 text-[14.5px] text-gris-700">
                Vuelve a entrar desde el enlace de tu correo, o empieza de nuevo tu registro.
              </p>
              <Link
                href="/registro"
                className="mt-6 inline-flex min-h-[44px] items-center justify-center rounded-full bg-azul-700 px-7 text-[15px] font-bold text-blanco hover:bg-azul-800"
              >
                Ir al registro
              </Link>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
