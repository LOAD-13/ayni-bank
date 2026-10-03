"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { IndicadorDeProgreso } from "@/componentes/IndicadorDeProgreso";
import { LogotipoAyni } from "@/componentes/LogotipoAyni";

import { ConfirmacionDeDatosDelDni } from "./ConfirmacionDeDatosDelDni";

interface Props {
  solicitudId: string | undefined;
  pasoActual: number;
}

/**
 * El marco de página de la confirmación de datos del DNI. Sigue dentro del paso del
 * reverso en el indicador: confirmar lo leído es la última parte de verificar el documento.
 */
export function PasoDeConfirmacionDeDatos({ solicitudId, pasoActual }: Props) {
  const router = useRouter();

  return (
    <div className="min-h-screen bg-azul-050">
      <header className="border-b border-gris-300 bg-blanco">
        <div className="mx-auto flex max-w-[1440px] items-center px-6 py-5 lg:px-16">
          <Link href="/">
            <LogotipoAyni altura={16} tono="oscuro" />
          </Link>
        </div>
      </header>

      <main className="mx-auto flex max-w-[1440px] flex-col items-center gap-9 px-6 py-9 lg:px-16">
        <IndicadorDeProgreso pasoActual={pasoActual} />

        <div className="w-full max-w-[920px] rounded-[20px] border border-gris-300 bg-blanco p-6 shadow-sm sm:p-10">
          {solicitudId ? (
            <ConfirmacionDeDatosDelDni
              solicitudId={solicitudId}
              onConfirmado={() =>
                router.push(`/registro/prueba-de-vida?solicitudId=${solicitudId}`)
              }
              onDerivada={(estado) =>
                router.push(
                  `/registro/en-revision?motivo=${estado === "VERIFICACION_DIFERIDA" ? "diferida" : "revision"}`,
                )
              }
            />
          ) : (
            <div className="text-center">
              <h1 className="text-[22px] font-bold text-azul-800">
                No sabemos de qué solicitud hablas
              </h1>
              <p className="mt-2 text-[14.5px] text-gris-700">
                Vuelve a entrar desde el enlace de tu correo, o empieza de nuevo tu registro.
              </p>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
