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
 * Al terminar lleva al ingreso con el aviso de verificación completada: la banca ya sabe,
 * con la sesión del titular, si la cuenta quedó abierta o si la solicitud está en revisión.
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
      siguienteHref="/ingresar?verificacion=completada"
    />
  );
}
