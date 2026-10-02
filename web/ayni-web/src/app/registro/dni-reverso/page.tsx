import type { Metadata } from "next";

import { PasoDeCapturaDeDni } from "@/componentes/kyc/PasoDeCapturaDeDni";

export const metadata: Metadata = {
  title: "Foto del reverso de tu DNI",
  description: "Sube una foto clara del reverso de tu DNI para verificar tu identidad.",
  robots: { index: false, follow: false },
};

/** HU-02 · paso 3 de 5 del onboarding: reverso del DNI (AYNI-13 subtarea 12). */
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
      siguienteHref={`/registro/prueba-de-vida?solicitudId=${solicitudId ?? ""}`}
    />
  );
}
