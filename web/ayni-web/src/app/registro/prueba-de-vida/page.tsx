import type { Metadata } from "next";

import { PasoDeCapturaDeSelfie } from "@/componentes/kyc/PasoDeCapturaDeSelfie";

export const metadata: Metadata = {
  title: "Prueba de vida",
  description: "Verifica tu identidad con una selfie para completar tu registro.",
  robots: { index: false, follow: false },
};

/**
 * HU-03 · paso 4 de 5 del onboarding: consentimiento biométrico y selfie (AYNI-14).
 *
 * Al terminar, la selfie queda en manos de un operador que la compara con la foto del DNI
 * y aprueba la apertura: la pantalla de revisión lo explica y el correo avisa del resultado.
 */
export default async function PruebaDeVida({
  searchParams,
}: {
  searchParams: Promise<{ solicitudId?: string }>;
}) {
  const { solicitudId } = await searchParams;

  return (
    <PasoDeCapturaDeSelfie
      solicitudId={solicitudId}
      pasoActual={4}
      siguienteHref="/registro/en-revision?motivo=cotejo"
    />
  );
}
