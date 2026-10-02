import type { Metadata } from "next";

import { PasoDeCapturaDeDni } from "@/componentes/kyc/PasoDeCapturaDeDni";

export const metadata: Metadata = {
  title: "Foto del anverso de tu DNI",
  description: "Sube una foto clara del anverso de tu DNI para verificar tu identidad.",
  robots: { index: false, follow: false },
};

/** HU-02 · paso 2 de 5 del onboarding: anverso del DNI (AYNI-13 subtarea 12). */
export default async function DniAnverso({
  searchParams,
}: {
  searchParams: Promise<{ solicitudId?: string }>;
}) {
  const { solicitudId } = await searchParams;

  return (
    <PasoDeCapturaDeDni
      solicitudId={solicitudId}
      pasoActual={2}
      tipoDocumento="ANVERSO"
      cara="Anverso"
      siguienteHref={`/registro/dni-reverso?solicitudId=${solicitudId ?? ""}`}
    />
  );
}
