import type { Metadata } from "next";

import { PasoDeCapturaDeDni } from "@/componentes/kyc/PasoDeCapturaDeDni";

export const metadata: Metadata = {
  title: "Foto del reverso de tu DNI",
  description: "Sube una foto clara del reverso de tu DNI para verificar tu identidad.",
  robots: { index: false, follow: false },
};

/**
 * HU-02 · paso 3 de 5 del onboarding: reverso del DNI (AYNI-13 subtarea 12).
 *
 * Al terminar va a `/pendiente`: el paso 4 («Prueba de vida») es HU-03 (`AYNI-14`), otra
 * Historia de Usuario, todavía no construida. Enlazar a una pantalla que dice la verdad es
 * preferible a una ruta que no existe — mismo criterio que el resto del sitio.
 */
export default async function DniReverso({
  searchParams,
}: {
  searchParams: Promise<{ solicitudId?: string }>;
}) {
  const { solicitudId } = await searchParams;

  return (
    <PasoDeCapturaDeDni
      solicitudId={solicitudId}
      pasoActual={3}
      tipoDocumento="REVERSO"
      cara="Reverso"
      siguienteHref="/pendiente"
    />
  );
}
